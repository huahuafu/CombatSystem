package com.military.combat.simulation;

import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 六阶段作战判定服务：根据当前快照判定引擎主导阶段。
 */
@Service
public class OperationalPhaseService {

    @Autowired
    private ScenarioService scenarioService;
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

    public OperationalPhaseState current() {
        String sid = scenarioService.getActiveScenarioId();
        int round = scenarioService.getCurrentRound();
        if (sid == null || sid.isEmpty()) {
            return new OperationalPhaseState(
                    null,
                    round,
                    "US_KILL_CHAIN_6_STAGE",
                    KillChainPhase.FIND,
                    KillChainPhase.FIND.getDisplayName(),
                    "未激活想定，默认从发现阶段开始。"
            );
        }

        var find = findService.getFindSnapshot();
        var fix = fixService.getFixSnapshot();
        var track = trackService.getTrackSnapshot();
        var target = targetService.getTargetSnapshot();
        var engage = engageService.getEngageSnapshot();
        var assess = assessService.getAssessSnapshot();

        KillChainPhase phase;
        String reason;
        if (find.getDetectionsThisRound() <= 0 || find.getDetectionProbability() < 0.5) {
            phase = KillChainPhase.FIND;
            reason = "侦察接触不足，优先强化发现。";
        } else if (fix.getFixedTargetCount() <= 0 || fix.getMultiSourceFusionRate() < 0.45) {
            phase = KillChainPhase.FIX;
            reason = "有效定位目标不足，优先完成多源融合定位。";
        } else if (track.getTrackedTargetCount() <= 0 || track.getTrackContinuityRate() < 0.5) {
            phase = KillChainPhase.TRACK;
            reason = "稳定跟踪不足，优先确保轨迹连续。";
        } else if (target.getFireSolutionCount() <= 0 || target.getAssignmentCoverageRate() < 0.45) {
            phase = KillChainPhase.TARGET;
            reason = "火力解准备不足，优先完成目标分配与武器匹配。";
        } else if (engage.getExecutedStrikeCount() <= 0) {
            // 尚无持久化交战记录时仍以交战为主导；一旦有交战事件即转入评估（不再卡 0.45 兑现率阈值）
            phase = KillChainPhase.ENGAGE;
            reason = "尚未记录交战事件，优先组织火力打击。";
        } else {
            phase = KillChainPhase.ASSESS;
            reason = "已具备交战记录，进入战果评估与闭环修正。"
                    + " 当前闭环完成度 " + (int) Math.round(assess.getLoopClosureRate() * 100) + "%。";
        }

        return new OperationalPhaseState(
                sid,
                round,
                "US_KILL_CHAIN_6_STAGE",
                phase,
                phase.getDisplayName(),
                reason
        );
    }
}
