package com.military.combat.service;

import com.military.combat.entity.BattleEvent;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import com.military.combat.simulation.batch.BatchRunOutcome;
import com.military.combat.simulation.batch.BatchSimulationRequest;
import com.military.combat.simulation.batch.BatchSimulationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 同想定多次重放 + 简单战损差分评分，为「多方案/多随机寻优」提供基座。
 */
@Service
public class BatchSimulationService {

    private static final int MAX_RUNS = 50;
    private static final int MAX_ROUNDS = 200;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private ScenarioActivationService scenarioActivationService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatEngineService combatEngineService;

    @Autowired
    private CombatActivityService combatActivityService;

    @Autowired
    private InteractionRuleService interactionRuleService;

    private static final String AI_ARTIFACT_PREFIX = "[AI]";

    public BatchSimulationResponse run(BatchSimulationRequest req) {
        if (req == null) {
            req = new BatchSimulationRequest();
        }
        String sid = req.getScenarioId();
        if (sid == null || sid.isEmpty()) {
            sid = scenarioService.getActiveScenarioId();
        }
        if (sid == null || sid.isEmpty()) {
            throw new IllegalArgumentException("未指定想定且当前无激活想定，无法执行批量模拟。");
        }

        int runs = clamp(req.getRuns(), 1, MAX_RUNS);
        int roundsPerRun = clamp(req.getRoundsPerRun(), 1, MAX_ROUNDS);
        boolean redPerspective = !"BLUE".equalsIgnoreCase(
                req.getScorePerspective() == null ? "RED" : req.getScorePerspective().trim());

        BatchSimulationResponse out = new BatchSimulationResponse();
        out.setScenarioId(sid);
        out.setRunsRequested(runs);
        out.setRoundsPerRun(roundsPerRun);
        out.setScoreDescription(redPerspective
                ? "score ≈ (蓝方战损) - 0.65×(红方战损)，并微调目标达成数。"
                : "score ≈ (红方战损) - 0.65×(蓝方战损)，并微调目标达成数。");

        long baseSeed = (req.getSeed() != null) ? req.getSeed()
                : System.currentTimeMillis() ^ (sid.hashCode() * 2654435761L);
        out.setSeed(baseSeed);

        if (req.isReplaceAiArtifacts()) {
            int pa = combatActivityService.deleteActivitiesWithNamePrefixForScenario(sid, AI_ARTIFACT_PREFIX);
            int pr = interactionRuleService.deleteRulesWithNamePrefixForScenario(sid, AI_ARTIFACT_PREFIX);
            out.setPurgeSummary(String.format(
                    "运行前已删除名称前缀 %s 的作战活动 %d 条、交互规则 %d 条。", AI_ARTIFACT_PREFIX, pa, pr));
        }

        for (int i = 0; i < runs; i++) {
            long runSeed = mixSeed(baseSeed, i);
            scenarioService.setSimulationSeed(runSeed);
            scenarioService.clearRoundAndStatsKeepActive();
            scenarioService.resetObjectiveCompletionFlags(sid);
            combatActivityService.resetActivitiesForReplay(sid);
            scenarioActivationService.activateScenario(sid);

            List<CombatUnit> units0 = combatUnitService.getUnitsForBattleEngine();
            int rs = sumCombatPower(units0, "RED");
            int bs = sumCombatPower(units0, "BLUE");

            int eventsTotal = 0;
            for (int r = 0; r < roundsPerRun; r++) {
                List<BattleEvent> ev = combatEngineService.simulateRound();
                eventsTotal += ev == null ? 0 : ev.size();
            }

            List<CombatUnit> units1 = combatUnitService.getUnitsForBattleEngine();
            int re = sumCombatPower(units1, "RED");
            int be = sumCombatPower(units1, "BLUE");

            ScenarioData sd = scenarioService.getScenarioDataById(sid);
            int objDone = countCompletedObjectives(sd);

            BatchRunOutcome o = new BatchRunOutcome();
            o.setRunIndex(i);
            o.setRoundsSimulated(roundsPerRun);
            o.setSeed(runSeed);
            o.setRedPowerAtStart(rs);
            o.setBluePowerAtStart(bs);
            o.setRedPowerAtEnd(re);
            o.setBluePowerAtEnd(be);
            o.setTotalBattleEvents(eventsTotal);
            o.setObjectivesCompleted(objDone);
            o.setScore(computeScore(redPerspective, rs, bs, re, be, objDone));

            out.getOutcomes().add(o);
        }

        restoreCleanStateAfterBatch(sid);

        BatchRunOutcome best = out.getOutcomes().stream()
                .max(Comparator.comparingDouble(BatchRunOutcome::getScore))
                .orElse(null);
        if (best != null) {
            out.setBestRunIndex(best.getRunIndex());
        }

        out.getOutcomes().sort(Comparator.comparingDouble(BatchRunOutcome::getScore).reversed());
        return out;
    }

    private static long mixSeed(long baseSeed, int runIndex) {
        long z = baseSeed + 0x9E3779B97F4A7C15L * (runIndex + 1L);
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * 结束时恢复：玩家继续手动推演时面对的是「干净」初始化态。
     */
    private void restoreCleanStateAfterBatch(String sid) {
        scenarioService.clearRoundAndStatsKeepActive();
        scenarioService.resetObjectiveCompletionFlags(sid);
        combatActivityService.resetActivitiesForReplay(sid);
        scenarioActivationService.activateScenario(sid);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int sumCombatPower(List<CombatUnit> units, String side) {
        if (units == null) {
            return 0;
        }
        String s = side.toUpperCase(Locale.ROOT);
        int sum = 0;
        for (CombatUnit u : units) {
            if (u != null && s.equals(u.getSide())) {
                sum += Math.max(0, u.getCombatPower());
            }
        }
        return sum;
    }

    private static int countCompletedObjectives(ScenarioData sd) {
        if (sd == null || sd.getObjectives() == null) {
            return 0;
        }
        int n = 0;
        for (CombatObjective o : sd.getObjectives()) {
            if (o != null && o.isCompleted()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 简单可解释分值：己方造成敌方战损为主，己方损失为次；完成的想定目标略加分。
     */
    private static double computeScore(boolean redPerspective,
                                       int rs, int bs, int re, int be,
                                       int objectivesCompleted) {
        double dmgEnemy;
        double dmgSelf;
        if (redPerspective) {
            dmgEnemy = Math.max(0, bs - be);
            dmgSelf = Math.max(0, rs - re);
        } else {
            dmgEnemy = Math.max(0, rs - re);
            dmgSelf = Math.max(0, bs - be);
        }
        double base = dmgEnemy - 0.65 * dmgSelf;
        return round3(base + objectivesCompleted * 50.0);
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
