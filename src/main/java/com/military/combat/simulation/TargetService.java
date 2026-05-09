package com.military.combat.simulation;

import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Target（瞄准）环节：目标分配与火力解准备质量评估。
 */
@Service
public class TargetService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private SortieEffectService sortieEffectService;

    @Autowired
    private CommandEffectService commandEffectService;

    @Autowired
    private OpposingEffectService opposingEffectService;

    public TargetSnapshot runTargetingForCurrentRound() {
        return getTargetSnapshot();
    }

    public TargetSnapshot getTargetSnapshot() {
        TargetSnapshot out = new TargetSnapshot();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setRound(scenarioService.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            out.setReadinessLevel("LOW");
            out.getWeaknesses().add("未激活想定，瞄准链路未启动。");
            return out;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        out.setScenarioName(sd != null ? sd.getName() : null);

        TrackSnapshot track = trackService.getTrackSnapshot();
        int tracked = Math.max(0, track.getTrackedTargetCount());
        out.setTrackedCandidateCount(tracked);

        List<CombatActivity> activities = activityService.getActivitiesForEditor(sid);
        List<CombatActivity> attackActivities = activities.stream()
                .filter(this::isAttackLike)
                .collect(Collectors.toList());
        int assigned = (int) attackActivities.stream().filter(a -> a.getObjectiveId() != null && !a.getObjectiveId().isEmpty()).count();
        int fireSolution = (int) attackActivities.stream().filter(this::hasFireSolution).count();
        // 未配置打击类作战活动时（自由对抗 / 演示想定）：用跟踪目标数作为最小火力解与分配占位，避免杀伤链阶段永远卡在 TARGET
        if (attackActivities.isEmpty() && tracked > 0) {
            fireSolution = Math.max(fireSolution, tracked);
            assigned = Math.max(assigned, tracked);
        }
        double assignmentCoverage = tracked <= 0 ? 0 : (double) Math.min(tracked, assigned) / tracked;
        double weaponMatch = computeWeaponMatch();
        double ttf = estimateTimeToFireSeconds(attackActivities, weaponMatch);
        double hvPriority = computeHighValuePriorityRate(attackActivities, tracked);

        out.setTargetAssignedCount(assigned);
        out.setFireSolutionCount(fireSolution);
        SortieEffectService.SortieEffectSummary sortie = sortieEffectService.currentSummary();
        CommandEffectService.CommandEffectSummary cmd = commandEffectService.currentSummary();
        OpposingEffectService.OpposingEffectSummary opp = opposingEffectService.currentSummary();
        out.setAssignmentCoverageRate(round3(clamp01(assignmentCoverage + sortie.isrBoost() * 0.5 + cmd.isrBoost() * 0.6 - opp.counterRecon() * 0.5)));
        out.setWeaponMatchRate(round3(clamp01(weaponMatch + sortie.ewBoost() * 0.2 + cmd.ewBoost() * 0.25 - opp.ewSuppression() * 0.4)));
        out.setTimeToFireSeconds(round3(Math.max(8, ttf - sortie.isrBoost() * 20 - sortie.ewBoost() * 12 - cmd.isrBoost() * 18 - cmd.ewBoost() * 10 + opp.decoy() * 18 + opp.ewSuppression() * 10)));
        out.setHighValuePriorityRate(round3(clamp01(hvPriority + sortie.isrBoost() * 0.3 + cmd.strikeBoost() * 0.3 - opp.decoy() * 0.4)));

        evaluate(out);
        out.setReadinessLevel(deriveReadiness(out));
        return out;
    }

    private boolean isAttackLike(CombatActivity a) {
        if (a == null) return false;
        String t = a.getType() == null ? "" : a.getType().toUpperCase(Locale.ROOT);
        String m = a.getMission() == null ? "" : a.getMission().toUpperCase(Locale.ROOT);
        return "ATTACK".equals(t) || m.contains("打击") || m.contains("突击") || m.contains("FIRE");
    }

    private boolean hasFireSolution(CombatActivity a) {
        if (a == null) return false;
        boolean hasGeo = a.getTargetLatitude() != null && a.getTargetLongitude() != null;
        boolean hasUnits = a.getUnitIds() != null && !a.getUnitIds().isEmpty();
        boolean hasSteps = a.getSteps() != null && !a.getSteps().isEmpty();
        return hasGeo && hasUnits && hasSteps;
    }

    private double computeWeaponMatch() {
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine().stream()
                .filter(u -> u != null && u.getCombatPower() > 0)
                .collect(Collectors.toList());
        if (units.isEmpty()) return 0;
        long withWeapon = units.stream().filter(u -> u.getWeapons() != null && !u.getWeapons().isEmpty()).count();
        return clamp01((double) withWeapon / units.size());
    }

    private double estimateTimeToFireSeconds(List<CombatActivity> attackActivities, double weaponMatch) {
        double base = 75;
        if (!attackActivities.isEmpty()) {
            double avgStep = attackActivities.stream()
                    .mapToInt(a -> a.getSteps() == null ? 0 : a.getSteps().size())
                    .average().orElse(0);
            base -= Math.min(25, avgStep * 2.0);
        }
        base -= weaponMatch * 20;
        return Math.max(10, base);
    }

    private double computeHighValuePriorityRate(List<CombatActivity> attackActivities, int tracked) {
        if (tracked <= 0 || attackActivities.isEmpty()) {
            return 0;
        }
        long priorityActs = attackActivities.stream()
                .filter(a -> (a.getMission() != null && (a.getMission().contains("高价值") || a.getMission().contains("关键")))
                        || (a.getObjectiveId() != null && !a.getObjectiveId().isEmpty()))
                .count();
        return clamp01((double) priorityActs / Math.max(1, attackActivities.size()));
    }

    private void evaluate(TargetSnapshot out) {
        if (out.getAssignmentCoverageRate() >= 0.85) {
            out.getStrengths().add("目标分配覆盖率达标（>=85%）。");
        } else {
            out.getWeaknesses().add("目标分配覆盖不足（当前 " + pct(out.getAssignmentCoverageRate()) + "）。");
        }
        if (out.getWeaponMatchRate() >= 0.75) {
            out.getStrengths().add("武器-目标匹配质量较好。");
        } else {
            out.getWeaknesses().add("武器匹配度偏低（当前 " + pct(out.getWeaponMatchRate()) + "）。");
        }
        if (out.getTimeToFireSeconds() <= 60) {
            out.getStrengths().add("瞄准到交战准备时长达标（<=60秒）。");
        } else {
            out.getWeaknesses().add("瞄准准备偏慢（当前 " + round3(out.getTimeToFireSeconds()) + " 秒）。");
        }
        if (out.getHighValuePriorityRate() >= 0.7) {
            out.getStrengths().add("高价值目标优先级调度有效。");
        } else {
            out.getWeaknesses().add("高价值目标优先策略不足（当前 " + pct(out.getHighValuePriorityRate()) + "）。");
        }
        if (out.getFireSolutionCount() == 0) {
            out.getWeaknesses().add("未形成火力解，Engage 环节将被阻塞。");
        }
    }

    private String deriveReadiness(TargetSnapshot out) {
        int weakness = out.getWeaknesses().size();
        if (weakness <= 1) return "HIGH";
        if (weakness <= 3) return "MEDIUM";
        return "LOW";
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }
}
