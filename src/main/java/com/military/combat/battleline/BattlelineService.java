package com.military.combat.battleline;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import com.military.combat.service.CampaignService;
import com.military.combat.service.CombatActivityService;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.InteractionProcessService;
import com.military.combat.service.InteractionRuleService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 战役主线聚合：把「自定义战役」—「想定兵力部署」—「活动命令」—「交互规则」串成一条可解释的叙事链。
 */
@Service
public class BattlelineService {

    public static final String MAINLINE_TEMPLATE =
            "战役「%s」→ 想定「%s」兵力部署 → 作战活动命令 → 交互规则";

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CombatActivityService activityService;

    @Autowired
    private InteractionRuleService ruleService;

    @Autowired
    private InteractionProcessService processService;

    @Autowired
    private CombatUnitService combatUnitService;

    /**
     * 当前活跃想定对应的主线（无活跃想定则返回空壳说明）。
     */
    public BattlelineOverview getForActiveScenario() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            return emptyOverview("未设置当前工作想定");
        }
        return buildForScenarioId(sid);
    }

    private static BattlelineOverview emptyOverview(String reason) {
        BattlelineOverview o = new BattlelineOverview();
        o.setMainLine("主线：未就绪 — " + reason);
        o.setLayerCampaign("战役：未绑定");
        o.setLayerDeployment("想定兵力部署：—");
        o.setLayerCommands("作战活动命令：—");
        o.setLayerInteraction("交互规则：—");
        return o;
    }

    public BattlelineOverview buildForScenarioId(String scenarioId) {
        ScenarioData sd = scenarioService.getScenarioDataById(scenarioId);
        if (sd == null) {
            return emptyOverview("想定不存在");
        }
        Campaign c = sd.getCampaign();
        Campaign session = campaignService.getCurrentCampaign();
        String cid = c != null ? c.getId() : sd.getCampaignId();
        String cname = c != null ? c.getName() : null;
        if ((cname == null || cname.isEmpty()) && session != null && cid != null && cid.equals(session.getId())) {
            cname = session.getName();
        }
        if (cname == null || cname.isEmpty()) {
            cname = "未命名战役";
        }

        int red = 0;
        int blue = 0;
        List<CombatUnit> units = sd.getUnits();
        if (units != null) {
            for (CombatUnit u : units) {
                if (u == null || u.getSide() == null) {
                    continue;
                }
                if ("RED".equals(u.getSide())) {
                    red++;
                } else if ("BLUE".equals(u.getSide())) {
                    blue++;
                }
            }
        }

        if (units == null || units.isEmpty()) {
            red = countSideFromDb("RED");
            blue = countSideFromDb("BLUE");
        }

        int objCount = 0;
        List<CombatObjective> objs = sd.getObjectives();
        if (objs != null) {
            objCount = objs.size();
        }

        int actCount = activityService.getActivitiesForEditor(scenarioId).size();
        int ruleCount = ruleService.getInteractionRulesForEditor(scenarioId).size();
        int procCount = processService.getProcessesForEditor(scenarioId).size();

        BattlelineOverview o = new BattlelineOverview();
        o.setCampaignId(cid);
        o.setCampaignName(cname);
        o.setScenarioId(scenarioId);
        o.setScenarioName(sd.getName() != null ? sd.getName() : scenarioId);
        o.setRedUnitCount(red);
        o.setBlueUnitCount(blue);
        o.setObjectiveCount(objCount);
        o.setActivityCount(actCount);
        o.setInteractionRuleCount(ruleCount);
        o.setInteractionProcessCount(procCount);

        o.setLayerCampaign("战役：「" + cname + "」" + (cid != null ? "（ID：" + cid + "）" : ""));
        o.setLayerDeployment(String.format(
                "想定兵力部署：红方 %d 个单位，蓝方 %d 个单位；作战目标 %d 条",
                red, blue, objCount));
        o.setLayerCommands(String.format("作战活动命令：%d 条活动（对当前想定过滤）", actCount));
        o.setLayerInteraction(String.format(
                "交互建模：%d 条规则，%d 条交互过程（可含全局规则）",
                ruleCount, procCount));

        o.setMainLine(String.format(MAINLINE_TEMPLATE, cname, o.getScenarioName()));

        o.getHints().add("想定建模：在「想定」中保存本战役的兵力、目标与地形，并绑定内嵌战役文档。");
        o.getHints().add("活动建模：创建活动并指定 scenarioId / campaignId，即对本战役部队下达命令。");
        o.getHints().add("交互建模：规则与过程绑定想定或留空为全局，推演时由引擎按优先级匹配。");
        return o;
    }

    private int countSideFromDb(String side) {
        return (int) combatUnitService.getVisibleUnitsForList().stream()
                .filter(u -> side.equals(u.getSide()))
                .count();
    }

    /**
     * 供 {@link com.military.combat.simulation.SimulationFacadeService} 使用的一句话主线。
     */
    public String getMainLineForActiveScenario() {
        return getForActiveScenario().getMainLine();
    }

    public List<ScenarioData> listScenariosForCampaign(String campaignId) {
        if (campaignId == null || campaignId.isEmpty()) {
            return List.of();
        }
        return scenarioService.findScenariosByCampaignId(campaignId);
    }
}
