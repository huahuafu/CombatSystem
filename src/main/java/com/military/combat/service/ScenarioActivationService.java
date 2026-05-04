package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionEvent;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.InteractionEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Autowired
    private CombatActivityService combatActivityService;

    @Autowired
    private InteractionEventRepository interactionEventRepository;

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

    /**
     * 将指定想定恢复到「想定文档内保存的」初始态势：清空会话回合/统计、目标完成标记、胜负记录、
     * 作战活动执行态、与本想定兵力相关的交互事件，并按想定内兵力重新投影战场单位。
     * <p>与批量推演每轮开局的准备逻辑一致，供用户在多次仿真后一键回到起点。</p>
     */
    public void resetScenarioToInitialState(String scenarioId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            throw new IllegalArgumentException("想定ID不能为空");
        }
        String sid = scenarioId.trim();
        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        if (sd == null) {
            throw new IllegalArgumentException("想定不存在: " + sid);
        }
        purgeInteractionEventsForScenarioUnits(sd);
        scenarioService.clearRoundAndStatsKeepActive();
        scenarioService.resetObjectiveCompletionFlags(sid);
        scenarioService.clearWinnerAndReasonInScenarioDocument(sid);
        combatActivityService.resetActivitiesForReplay(sid);
        activateScenario(sid);
    }

    private void purgeInteractionEventsForScenarioUnits(ScenarioData sd) {
        if (sd == null || sd.getUnits() == null) {
            return;
        }
        Set<String> ids = new HashSet<>();
        for (CombatUnit u : sd.getUnits()) {
            if (u != null && u.getId() != null && !u.getId().isEmpty()) {
                ids.add(u.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        List<InteractionEvent> snapshot = new ArrayList<>(interactionEventRepository.findAll());
        for (InteractionEvent e : snapshot) {
            if (e == null || e.getId() == null) {
                continue;
            }
            String src = e.getSourceUnitId();
            String tgt = e.getTargetUnitId();
            if ((src != null && ids.contains(src)) || (tgt != null && ids.contains(tgt))) {
                interactionEventRepository.deleteById(e.getId());
            }
        }
    }
}

