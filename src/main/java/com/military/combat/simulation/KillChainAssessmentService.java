package com.military.combat.simulation;

import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionEvent;
import com.military.combat.entity.InteractionRule;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.InteractionEventRepository;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.InteractionRuleService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 将现有推演能力按 Find/Fix/Track/Target/Engage/Assess 六环节重组。
 */
@Service
public class KillChainAssessmentService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

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

    @Autowired
    private AssessService assessService;

    public KillChainAssessment getForActiveScenario() {
        KillChainAssessment out = new KillChainAssessment();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);
        out.setCurrentRound(scenarioService.getCurrentRound());

        if (sid == null || sid.isEmpty()) {
            out.setScenarioName("未激活想定");
            out.setOverallMaturity(0);
            out.setDoctrineTip("先激活想定，再补齐侦察活动、目标定位、跟踪规则与交战规则。");
            out.setStages(buildEmptyStages());
            return out;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        out.setScenarioName(sd != null ? sd.getName() : null);

        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        List<CombatActivity> activities = activityService.getActivitiesForEditor(sid);
        List<InteractionRule> rules = ruleService.getInteractionRulesForEditor(sid);
        List<InteractionEvent> events = interactionEventRepository.findAll();
        TacticalAssessment tactical = tacticalAssessmentService.getForActiveScenario();

        List<KillChainStageStatus> stages = new ArrayList<>();
        FindSnapshot findSnapshot = findService.getFindSnapshot();
        stages.add(evalFind(findSnapshot));
        FixSnapshot fixSnapshot = fixService.getFixSnapshot();
        stages.add(evalFix(fixSnapshot));
        TrackSnapshot trackSnapshot = trackService.getTrackSnapshot();
        stages.add(evalTrack(trackSnapshot));
        TargetSnapshot targetSnapshot = targetService.getTargetSnapshot();
        stages.add(evalTarget(targetSnapshot));
        EngageSnapshot engageSnapshot = engageService.getEngageSnapshot();
        stages.add(evalEngage(engageSnapshot));
        AssessSnapshot assessSnapshot = assessService.getAssessSnapshot();
        stages.add(evalAssess(assessSnapshot, tactical));
        applySequentialGating(stages);
        out.setStages(stages);

        double avg = stages.stream().mapToDouble(KillChainStageStatus::getProgress).average().orElse(0);
        out.setOverallMaturity(round3(avg));
        out.setDoctrineTip(makeDoctrineTip(stages));
        return out;
    }

    private KillChainStageStatus evalFind(FindSnapshot findSnapshot) {
        KillChainStageStatus s = base("FIND", "发现");
        if (findSnapshot == null) {
            s.getGaps().add("Find 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += findSnapshot.getDetectionProbability() * 0.35;
        score += (1 - findSnapshot.getFalseAlarmRate()) * 0.15;
        score += findSnapshot.getIdentificationAccuracy() * 0.20;
        score += Math.max(0, Math.min(1, (60 - findSnapshot.getReportingLatencySeconds()) / 60.0)) * 0.15;
        score += Math.max(0, Math.min(1, (10 - findSnapshot.getUpdateIntervalSeconds()) / 10.0)) * 0.15;

        s.getEvidences().add("探测概率 " + pct(findSnapshot.getDetectionProbability()) + "。");
        s.getEvidences().add("识别准确率 " + pct(findSnapshot.getIdentificationAccuracy()) + "。");
        s.getEvidences().add("上报时延 " + round1(findSnapshot.getReportingLatencySeconds()) + " 秒。");
        s.getEvidences().add("数据更新间隔 " + round1(findSnapshot.getUpdateIntervalSeconds()) + " 秒。");
        if (findSnapshot.getStrengths() != null) {
            s.getEvidences().addAll(findSnapshot.getStrengths().stream().limit(2).collect(Collectors.toList()));
        }
        if (findSnapshot.getWeaknesses() != null) {
            s.getGaps().addAll(findSnapshot.getWeaknesses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private KillChainStageStatus evalFix(FixSnapshot fixSnapshot) {
        KillChainStageStatus s = base("FIX", "定位");
        if (fixSnapshot == null) {
            s.getGaps().add("Fix 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += fixSnapshot.getMultiSourceFusionRate() * 0.35;
        score += fixSnapshot.getGeoConsistencyRate() * 0.30;
        score += Math.max(0, Math.min(1, (800 - fixSnapshot.getAvgPositionErrorMeters()) / 800.0)) * 0.20;
        score += Math.max(0, Math.min(1, fixSnapshot.getFixedTargetCount() / 3.0)) * 0.15;

        s.getEvidences().add("多源融合率 " + pct(fixSnapshot.getMultiSourceFusionRate()) + "。");
        s.getEvidences().add("定位一致性 " + pct(fixSnapshot.getGeoConsistencyRate()) + "。");
        s.getEvidences().add("平均误差 " + round1(fixSnapshot.getAvgPositionErrorMeters()) + "m。");
        s.getEvidences().add("可移交目标 " + fixSnapshot.getFixedTargetCount() + " 个。");
        if (fixSnapshot.getWeaknesses() != null) {
            s.getGaps().addAll(fixSnapshot.getWeaknesses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private KillChainStageStatus evalTrack(TrackSnapshot trackSnapshot) {
        KillChainStageStatus s = base("TRACK", "跟踪");
        if (trackSnapshot == null) {
            s.getGaps().add("Track 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += trackSnapshot.getTrackContinuityRate() * 0.40;
        score += (1 - trackSnapshot.getTrackLossRate()) * 0.25;
        score += Math.max(0, Math.min(1, (10 - trackSnapshot.getTrackUpdateSeconds()) / 10.0)) * 0.20;
        score += trackSnapshot.getPredictionStabilityRate() * 0.15;

        s.getEvidences().add("轨迹连续性 " + pct(trackSnapshot.getTrackContinuityRate()) + "。");
        s.getEvidences().add("丢轨率 " + pct(trackSnapshot.getTrackLossRate()) + "。");
        s.getEvidences().add("轨迹更新间隔 " + round1(trackSnapshot.getTrackUpdateSeconds()) + " 秒。");
        s.getEvidences().add("预测稳定性 " + pct(trackSnapshot.getPredictionStabilityRate()) + "。");
        if (trackSnapshot.getWeaknesses() != null) {
            s.getGaps().addAll(trackSnapshot.getWeaknesses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private KillChainStageStatus evalTarget(TargetSnapshot targetSnapshot) {
        KillChainStageStatus s = base("TARGET", "瞄准");
        if (targetSnapshot == null) {
            s.getGaps().add("Target 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += targetSnapshot.getAssignmentCoverageRate() * 0.35;
        score += targetSnapshot.getWeaponMatchRate() * 0.25;
        score += Math.max(0, Math.min(1, (60 - targetSnapshot.getTimeToFireSeconds()) / 60.0)) * 0.20;
        score += targetSnapshot.getHighValuePriorityRate() * 0.20;

        s.getEvidences().add("目标分配覆盖率 " + pct(targetSnapshot.getAssignmentCoverageRate()) + "。");
        s.getEvidences().add("武器匹配度 " + pct(targetSnapshot.getWeaponMatchRate()) + "。");
        s.getEvidences().add("预计交战准备时长 " + round1(targetSnapshot.getTimeToFireSeconds()) + " 秒。");
        s.getEvidences().add("高价值目标优先率 " + pct(targetSnapshot.getHighValuePriorityRate()) + "。");
        if (targetSnapshot.getWeaknesses() != null) {
            s.getGaps().addAll(targetSnapshot.getWeaknesses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private KillChainStageStatus evalEngage(EngageSnapshot engageSnapshot) {
        KillChainStageStatus s = base("ENGAGE", "交战");
        if (engageSnapshot == null) {
            s.getGaps().add("Engage 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += engageSnapshot.getFireExecutionRate() * 0.35;
        score += engageSnapshot.getHitEffectivenessRate() * 0.30;
        score += Math.max(0, Math.min(1, (60 - engageSnapshot.getEngageLatencySeconds()) / 60.0)) * 0.20;
        score += engageSnapshot.getCollateralControlRate() * 0.15;

        s.getEvidences().add("火力兑现率 " + pct(engageSnapshot.getFireExecutionRate()) + "。");
        s.getEvidences().add("命中效率 " + pct(engageSnapshot.getHitEffectivenessRate()) + "。");
        s.getEvidences().add("交战时延 " + round1(engageSnapshot.getEngageLatencySeconds()) + " 秒。");
        s.getEvidences().add("附带损伤控制率 " + pct(engageSnapshot.getCollateralControlRate()) + "。");
        if (engageSnapshot.getWeaknesses() != null) {
            s.getGaps().addAll(engageSnapshot.getWeaknesses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private KillChainStageStatus evalAssess(AssessSnapshot assessSnapshot, TacticalAssessment tactical) {
        KillChainStageStatus s = base("ASSESS", "评估");
        if (assessSnapshot == null) {
            s.getGaps().add("Assess 模块无快照数据。");
            finalizeStage(s, 0, 0.7);
            return s;
        }
        double score = 0;
        score += assessSnapshot.getMissionEffectivenessRate() * 0.35;
        score += assessSnapshot.getLoopClosureRate() * 0.30;
        score += (1 - assessSnapshot.getModelDeviationRate()) * 0.20;
        score += assessSnapshot.getBdaConfidenceRate() * 0.15;

        s.getEvidences().add("任务效能 " + pct(assessSnapshot.getMissionEffectivenessRate()) + "。");
        s.getEvidences().add("闭环完成度 " + pct(assessSnapshot.getLoopClosureRate()) + "。");
        s.getEvidences().add("偏差率 " + pct(assessSnapshot.getModelDeviationRate()) + "。");
        s.getEvidences().add("战果判定置信度 " + pct(assessSnapshot.getBdaConfidenceRate()) + "。");
        if (assessSnapshot.getDeviationCauses() != null) {
            s.getGaps().addAll(assessSnapshot.getDeviationCauses().stream().limit(3).collect(Collectors.toList()));
        }
        finalizeStage(s, score, 0.7);
        return s;
    }

    private boolean isReconLikeActivity(CombatActivity a) {
        if (a == null) return false;
        String type = upper(a.getType());
        String mission = upper(a.getMission());
        return "RECON".equals(type) || mission.contains("侦察") || mission.contains("搜索");
    }

    private boolean isAttackLikeActivity(CombatActivity a) {
        if (a == null) return false;
        String type = upper(a.getType());
        String mission = upper(a.getMission());
        return "ATTACK".equals(type) || mission.contains("打击") || mission.contains("火力") || mission.contains("突击");
    }

    private String upper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private KillChainStageStatus base(String key, String name) {
        KillChainStageStatus s = new KillChainStageStatus();
        s.setStageKey(key);
        s.setStageName(name);
        s.setProgress(0);
        s.setReady(false);
        return s;
    }

    private void finalizeStage(KillChainStageStatus s, double score, double readyThreshold) {
        double p = round3(Math.max(0, Math.min(1, score)));
        s.setProgress(p);
        s.setReady(p >= readyThreshold);
    }

    private List<KillChainStageStatus> buildEmptyStages() {
        List<KillChainStageStatus> list = new ArrayList<>();
        list.add(withGap(base("FIND", "发现"), "未激活想定"));
        list.add(withGap(base("FIX", "定位"), "未激活想定"));
        list.add(withGap(base("TRACK", "跟踪"), "未激活想定"));
        list.add(withGap(base("TARGET", "瞄准"), "未激活想定"));
        list.add(withGap(base("ENGAGE", "交战"), "未激活想定"));
        list.add(withGap(base("ASSESS", "评估"), "未激活想定"));
        return list;
    }

    private KillChainStageStatus withGap(KillChainStageStatus s, String gap) {
        s.getGaps().add(gap);
        return s;
    }

    private void applySequentialGating(List<KillChainStageStatus> stages) {
        if (stages == null || stages.isEmpty()) {
            return;
        }
        for (int i = 0; i < stages.size(); i++) {
            KillChainStageStatus cur = stages.get(i);
            KillChainStageStatus prev = i > 0 ? stages.get(i - 1) : null;
            if (prev == null) {
                cur.setPredecessorKey(null);
                cur.setBlockedByPrevious(false);
            } else {
                cur.setPredecessorKey(prev.getStageKey());
                cur.setBlockedByPrevious(!prev.isReady());
            }
            cur.setTransitionHint(buildTransitionHint(cur, prev));
        }
    }

    private String buildTransitionHint(KillChainStageStatus current, KillChainStageStatus previous) {
        if (current == null) {
            return "";
        }
        if (previous != null && !previous.isReady()) {
            return "前序环节 " + previous.getStageName() + " 未就绪，优先补齐其短板后再推进本环节。";
        }
        String key = current.getStageKey();
        if ("FIND".equals(key)) {
            return "补齐多源探测与识别质量后，进入定位（Fix）环节。";
        }
        if ("FIX".equals(key)) {
            return "完成目标地理锚定后，转入持续跟踪（Track）。";
        }
        if ("TRACK".equals(key)) {
            return "形成稳定轨迹后，进入瞄准（Target）进行目标分配。";
        }
        if ("TARGET".equals(key)) {
            return "瞄准参数就绪后，进入交战（Engage）执行打击。";
        }
        if ("ENGAGE".equals(key)) {
            return "交战结果沉淀后，进入评估（Assess）闭环复盘。";
        }
        return "评估结果回馈前端环节，驱动下一轮发现与计划修正。";
    }

    private String makeDoctrineTip(List<KillChainStageStatus> stages) {
        List<KillChainStageStatus> notReady = stages.stream()
                .filter(x -> !x.isReady())
                .collect(Collectors.toList());
        if (notReady.isEmpty()) {
            return "六环节链路已闭合，可继续强化跨环节时序与多源情报融合。";
        }
        String names = notReady.stream().map(KillChainStageStatus::getStageName).collect(Collectors.joining("、"));
        return "当前短板环节：" + names + "。建议优先补齐前段侦察定位，再提升交战与评估闭环。";
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private String pct(double v) {
        return (int) Math.round(v * 100) + "%";
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
