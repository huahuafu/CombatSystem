package com.military.combat.simulation;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Assess（评估）环节：融合前序环节指标生成闭环结论。
 */
@Service
public class AssessService {

    @Autowired
    private TacticalAssessmentService tacticalAssessmentService;

    @Autowired
    private FindService findService;

    @Autowired
    private FixService fixService;

    @Autowired
    private TrackService trackService;

    @Autowired
    private TargetService targetService;

    @Autowired
    private EngageService engageService;

    public AssessSnapshot runAssessNow() {
        return getAssessSnapshot();
    }

    public AssessSnapshot getAssessSnapshot() {
        AssessSnapshot out = new AssessSnapshot();
        TacticalAssessment tactical = tacticalAssessmentService.getForActiveScenario();
        FindSnapshot find = findService.getFindSnapshot();
        FixSnapshot fix = fixService.getFixSnapshot();
        TrackSnapshot track = trackService.getTrackSnapshot();
        TargetSnapshot target = targetService.getTargetSnapshot();
        EngageSnapshot engage = engageService.getEngageSnapshot();

        out.setScenarioId(tactical.getScenarioId());
        out.setScenarioName(tactical.getScenarioName());
        out.setRound(tactical.getCurrentRound());
        out.setGeneratedAt(System.currentTimeMillis());

        if (out.getScenarioId() == null || out.getScenarioId().isEmpty()) {
            out.setReadinessLevel("LOW");
            out.getDeviationCauses().add("未激活想定，Assess 环节无法形成闭环评估。");
            out.getNextActionRecommendations().add("先激活想定并完成至少 1 回合推演。");
            return out;
        }

        double missionEffectiveness =
                tactical.getObjectiveCompletionRate() * 0.35 +
                engage.getHitEffectivenessRate() * 0.35 +
                target.getAssignmentCoverageRate() * 0.30;
        double bdaConfidence =
                engage.getHitEffectivenessRate() * 0.4 +
                find.getIdentificationAccuracy() * 0.3 +
                track.getPredictionStabilityRate() * 0.3;
        double deviation =
                1 - (
                        engage.getFireExecutionRate() * 0.25 +
                        track.getTrackContinuityRate() * 0.25 +
                        fix.getGeoConsistencyRate() * 0.25 +
                        find.getDetectionProbability() * 0.25
                );
        double closure =
                avg(find.getDetectionProbability(),
                        fix.getMultiSourceFusionRate(),
                        track.getTrackContinuityRate(),
                        target.getAssignmentCoverageRate(),
                        engage.getFireExecutionRate());

        out.setMissionEffectivenessRate(round3(clamp01(missionEffectiveness)));
        out.setObjectiveAchievementRate(round3(clamp01(tactical.getObjectiveCompletionRate())));
        out.setBdaConfidenceRate(round3(clamp01(bdaConfidence)));
        out.setModelDeviationRate(round3(clamp01(deviation)));
        out.setLoopClosureRate(round3(clamp01(closure)));

        buildFindings(out, find, fix, track, target, engage, tactical);
        out.setReadinessLevel(deriveReadiness(out));
        return out;
    }

    private void buildFindings(AssessSnapshot out,
                               FindSnapshot find,
                               FixSnapshot fix,
                               TrackSnapshot track,
                               TargetSnapshot target,
                               EngageSnapshot engage,
                               TacticalAssessment tactical) {
        out.getKeyFindings().add("目标达成率 " + pct(tactical.getObjectiveCompletionRate()) + "，任务效能 " + pct(out.getMissionEffectivenessRate()) + "。");
        out.getKeyFindings().add("闭环完成度 " + pct(out.getLoopClosureRate()) + "，战果判定置信度 " + pct(out.getBdaConfidenceRate()) + "。");
        out.getKeyFindings().add("交战命中效率 " + pct(engage.getHitEffectivenessRate()) + "，交战时延 " + round1(engage.getEngageLatencySeconds()) + " 秒。");

        if (find.getDetectionProbability() < 0.9) {
            out.getDeviationCauses().add("Find 探测概率不足导致后续链路输入质量下降。");
        }
        if (fix.getGeoConsistencyRate() < 0.85) {
            out.getDeviationCauses().add("Fix 定位一致性不足导致目标坐标存在偏差。");
        }
        if (track.getTrackLossRate() > 0.1) {
            out.getDeviationCauses().add("Track 丢轨率偏高，Target 目标分配稳定性受影响。");
        }
        if (target.getAssignmentCoverageRate() < 0.85) {
            out.getDeviationCauses().add("Target 分配覆盖不足，火力解准备不充分。");
        }
        if (engage.getFireExecutionRate() < 0.8) {
            out.getDeviationCauses().add("Engage 火力兑现率偏低，战果输出受限。");
        }
        if (out.getDeviationCauses().isEmpty()) {
            out.getDeviationCauses().add("关键链路无明显短板，系统运行稳定。");
        }

        out.getNextActionRecommendations().add("优先修复偏差最大的前序环节，再推进后序环节。");
        out.getNextActionRecommendations().add("对低置信度目标执行再侦察与再定位，降低误差传播。");
        out.getNextActionRecommendations().add("依据 Assess 结果自动回填活动/规则参数，形成下一轮优化。");
    }

    private String deriveReadiness(AssessSnapshot out) {
        double score = out.getLoopClosureRate() * 0.4
                + (1 - out.getModelDeviationRate()) * 0.3
                + out.getMissionEffectivenessRate() * 0.3;
        if (score >= 0.8) return "HIGH";
        if (score >= 0.6) return "MEDIUM";
        return "LOW";
    }

    private double avg(double... values) {
        if (values == null || values.length == 0) return 0;
        double s = 0;
        for (double v : values) s += v;
        return s / values.length;
    }

    private double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }
}
