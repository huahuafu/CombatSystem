package com.military.combat.simulation;

import com.military.combat.entity.InteractionEvent;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Engage（交战）环节：评估交战执行与打击效果。
 */
@Service
public class EngageService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    @Autowired
    private OpposingEffectService opposingEffectService;

    public EngageSnapshot runEngageForCurrentRound() {
        return getEngageSnapshot();
    }

    public EngageSnapshot getEngageSnapshot() {
        EngageSnapshot out = new EngageSnapshot();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setRound(scenarioService.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            out.setReadinessLevel("LOW");
            out.getWeaknesses().add("未激活想定，交战链路未启动。");
            return out;
        }

        out.setScenarioName(scenarioService.getScenarioDataById(sid) != null
                ? scenarioService.getScenarioDataById(sid).getName() : null);

        TargetSnapshot target = targetService.getTargetSnapshot();
        int planned = Math.max(0, target.getFireSolutionCount());
        List<InteractionEvent> all = interactionEventRepository.findAll();
        // InteractionEvent.round 与 CombatEngine 内 ctx.currentRound+1 一致，即「本回合结束时」的 getCurrentRound() 值（非 +1 再偏移）
        int roundView = scenarioService.getCurrentRound();
        List<InteractionEvent> currentRound = all.stream()
                .filter(e -> e != null && e.getRound() == roundView)
                .toList();

        int executed = currentRound.size();
        int success = (int) currentRound.stream().filter(e -> "SUCCESS".equalsIgnoreCase(e.getResult())).count();
        int collateralRisk = (int) currentRound.stream()
                .filter(e -> e.getDescription() != null && (e.getDescription().contains("误伤") || e.getDescription().contains("民用")))
                .count();

        out.setPlannedFireSolutionCount(planned);
        out.setExecutedStrikeCount(executed);
        out.setSuccessfulHitCount(success);
        out.setCollateralRiskCount(collateralRisk);
        SortieEffectService.SortieEffectSummary sortie = sortieEffectService.currentSummary();
        CommandEffectService.CommandEffectSummary cmd = commandEffectService.currentSummary();
        OpposingEffectService.OpposingEffectSummary opp = opposingEffectService.currentSummary();
        out.setFireExecutionRate(round3(clamp01(rate(executed, planned) + sortie.strikeBoost() * 0.6 + cmd.strikeBoost() * 0.6 - opp.samIntercept() * 0.6)));
        out.setHitEffectivenessRate(round3(clamp01(rate(success, Math.max(1, executed)) + sortie.strikeBoost() * 0.7 + sortie.ewBoost() * 0.2 + cmd.strikeBoost() * 0.5 + cmd.ewBoost() * 0.2 - opp.samIntercept() * 0.5 - opp.decoy() * 0.3)));
        out.setEngageLatencySeconds(round3(Math.max(8, estimateEngageLatency(planned, executed) - sortie.strikeBoost() * 30 - cmd.strikeBoost() * 25 + opp.samIntercept() * 25)));
        out.setCollateralControlRate(round3(clamp01(1 - rate(collateralRisk, Math.max(1, executed)) + sortie.ewBoost() * 0.5 + cmd.airDefenseBoost() * 0.4 - opp.decoy() * 0.25)));

        evaluate(out);
        out.setReadinessLevel(deriveReadiness(out));
        return out;
    }

    private double estimateEngageLatency(int planned, int executed) {
        if (planned <= 0) return 90;
        double coverage = rate(executed, planned);
        double latency = 75 - coverage * 35;
        return Math.max(12, latency);
    }

    private void evaluate(EngageSnapshot out) {
        if (out.getFireExecutionRate() >= 0.8) {
            out.getStrengths().add("火力兑现率达标（>=80%）。");
        } else {
            out.getWeaknesses().add("火力兑现不足（当前 " + pct(out.getFireExecutionRate()) + "）。");
        }
        if (out.getHitEffectivenessRate() >= 0.75) {
            out.getStrengths().add("命中效率较好（>=75%）。");
        } else {
            out.getWeaknesses().add("命中效率偏低（当前 " + pct(out.getHitEffectivenessRate()) + "）。");
        }
        if (out.getEngageLatencySeconds() <= 60) {
            out.getStrengths().add("交战时延达标（<=60秒）。");
        } else {
            out.getWeaknesses().add("交战时延偏长（当前 " + round3(out.getEngageLatencySeconds()) + " 秒）。");
        }
        if (out.getCollateralControlRate() >= 0.9) {
            out.getStrengths().add("附带损伤控制良好（>=90%）。");
        } else {
            out.getWeaknesses().add("附带损伤控制不足（当前 " + pct(out.getCollateralControlRate()) + "）。");
        }
        if (out.getExecutedStrikeCount() == 0) {
            out.getWeaknesses().add("当前无交战执行记录，Assess 环节将缺少样本。");
        }
    }

    private String deriveReadiness(EngageSnapshot out) {
        int weakness = out.getWeaknesses().size();
        if (weakness <= 1) return "HIGH";
        if (weakness <= 3) return "MEDIUM";
        return "LOW";
    }

    private double rate(int a, int b) {
        if (b <= 0) return 0;
        return Math.max(0, Math.min(1, (double) a / b));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
