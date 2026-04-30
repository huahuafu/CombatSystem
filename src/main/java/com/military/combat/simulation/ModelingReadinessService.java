package com.military.combat.simulation;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CampaignObjective;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionRule;
import com.military.combat.entity.ScenarioData;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.CoordinationService;
import com.military.combat.service.InteractionRuleService;
import com.military.combat.service.InteractionProcessService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 推演前建模完整性检查。
 */
@Service
public class ModelingReadinessService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private InteractionProcessService interactionProcessService;

    @Autowired
    private CoordinationService coordinationService;

    public ModelingReadiness checkForActiveScenario() {
        ModelingReadiness out = new ModelingReadiness();
        String sid = scenarioService.getActiveScenarioId();
        out.setScenarioId(sid);

        if (sid == null || sid.isEmpty()) {
            out.getBlockers().add("未激活想定：请在想定建模中选择并激活一个想定。");
            out.setReady(false);
            return out;
        }

        ScenarioData sd = scenarioService.getScenarioDataById(sid);
        if (sd != null) {
            out.setScenarioName(sd.getName());
            out.setObjectiveCount(countTotalObjectives(sd));
        } else {
            out.getBlockers().add("当前想定不存在或已删除。");
        }

        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        int red = 0;
        int blue = 0;
        for (CombatUnit u : units) {
            if (u == null || u.getCombatPower() <= 0) {
                continue;
            }
            if ("RED".equals(u.getSide())) red++;
            if ("BLUE".equals(u.getSide())) blue++;
        }
        out.setRedUnitCount(red);
        out.setBlueUnitCount(blue);

        int activityCount = activityService.getActivitiesForEditor(sid).size();
        out.setActivityCount(activityCount);

        List<InteractionRule> rules = ruleService.getInteractionRulesForEditor(sid);
        int enabledRules = 0;
        for (InteractionRule rule : rules) {
            if (rule != null && rule.isEnabled()) {
                enabledRules++;
            }
        }
        out.setEnabledRuleCount(enabledRules);

        if (red == 0 || blue == 0) {
            out.getBlockers().add("双方兵力不完整：红方和蓝方都至少需要 1 个存活单位。");
        }
        if (out.getObjectiveCount() == 0) {
            out.getBlockers().add("缺少作战目标：请在想定中配置至少 1 个「作战目标」，或在战役模板（CAMPAIGN）的内嵌战役中配置战役目标（CAPTURE/DEFEND/DESTROY）。");
        }
        if (activityCount == 0) {
            out.getWarnings().add("未配置作战活动：可推演，但将以自由对抗为主。");
        }
        if (enabledRules == 0) {
            out.getWarnings().add("未启用交互规则：可推演，但交战将缺少规则加成。");
        }
        int processCount = interactionProcessService.getProcessesForEditor(sid).size();
        int coordinationCount = coordinationService.getCoordinationActionsForEditor(sid).size();
        if (processCount > 0 || coordinationCount > 0) {
            out.getWarnings().add("存在兼容模块数据（交互过程/协同作战），可能与规则主线产生重复效果。");
        }

        out.setReady(out.getBlockers().isEmpty());
        return out;
    }

    /**
     * 想定级作战目标（地图/CombatObjective）与内嵌战役目标（CampaignObjective）任一类有配置即可满足「有目标」。
     */
    private static int countTotalObjectives(ScenarioData sd) {
        if (sd == null) {
            return 0;
        }
        int n = 0;
        if (sd.getObjectives() != null) {
            for (CombatObjective o : sd.getObjectives()) {
                if (o != null) {
                    n++;
                }
            }
        }
        Campaign c = sd.getCampaign();
        if (c != null && c.getObjectives() != null) {
            for (CampaignObjective o : c.getObjectives()) {
                if (o != null) {
                    n++;
                }
            }
        }
        return n;
    }
}
