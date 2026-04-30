package com.military.combat.service;

import com.military.combat.entity.ScenarioData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 想定激活应用服务：负责把“当前工作想定”切换为单一事实来源，并将兵力部署投影到战场单位集合。
 */
@Service
public class ScenarioActivationService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CampaignService campaignService;

    /**
     * 原子化激活想定：设置 activeScenario -> 下发兵力部署 -> 同步战役会话。
     */
    public void activateScenario(String scenarioId) {
        scenarioService.setActiveScenarioId(scenarioId);
        if (scenarioId == null || scenarioId.isEmpty()) {
            campaignService.clearSessionCampaign();
            combatUnitService.clearLiveUnits();
            return;
        }
        ScenarioData sd = scenarioService.getScenarioDataById(scenarioId);
        if (sd == null) {
            throw new IllegalArgumentException("想定不存在: " + scenarioId);
        }
        combatUnitService.applyScenarioDeploymentToLive(sd);
        if (sd.getCampaign() != null) {
            campaignService.applyCampaignToSession(sd.getCampaign());
        } else {
            campaignService.clearSessionCampaign();
        }
    }
}

