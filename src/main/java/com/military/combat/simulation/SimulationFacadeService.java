package com.military.combat.simulation;

import com.military.combat.battleline.BattlelineService;
import com.military.combat.entity.Campaign;
import com.military.combat.service.CampaignService;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 推演会话的只读视图聚合：回合、想定、活动驱动模式、战役会话。
 * 自定义作战活动推演以 {@link CombatActivityService#getActiveActivities(int)} 为唯一判定来源。
 */
@Service
public class SimulationFacadeService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private BattlelineService battlelineService;

    @Autowired
    private OperationalPhaseService operationalPhaseService;

    public SimulationState getState() {
        int r = scenarioService.getCurrentRound();
        boolean td = activityService.hasTaskDrivingActivities(r);
        SimulationMode mode = td ? SimulationMode.TASK_DRIVEN : SimulationMode.FREE_FOR_ALL;
        String desc = td
                ? "本回合存在作战活动：编入活动的单位由活动步骤推进；未编入的单位仍按作战目标与默认战术自主行动。"
                : "本回合无符合条件的作战活动：全场单位按作战目标（若有）与战术规则自主交战。";

        Campaign c = campaignService.getCurrentCampaign();
        String cid = c != null ? c.getId() : null;
        String cname = c != null ? c.getName() : null;

        String mainLine = battlelineService.getMainLineForActiveScenario();
        OperationalPhaseState phase = operationalPhaseService.current();

        return new SimulationState(
                r,
                scenarioService.getActiveScenarioId(),
                mode,
                td,
                desc,
                cid,
                cname,
                mainLine,
                phase.getDoctrine(),
                phase.getCurrentPhase() == null ? null : phase.getCurrentPhase().name(),
                phase.getCurrentPhaseName(),
                scenarioService.getWinner(),
                scenarioService.getWinReason()
        );
    }
}
