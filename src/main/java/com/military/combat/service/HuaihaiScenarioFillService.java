package com.military.combat.service;

import com.military.combat.campaign.demo.CampaignDemoData;
import com.military.combat.campaign.demo.HuaihaiScenarioDefaults;
import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.InteractionRule;
import com.military.combat.entity.ScenarioData;
import com.military.combat.repository.CombatActivityRepository;
import com.military.combat.repository.InteractionRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将淮海战役演示数据写入<strong>已存在的</strong>想定：兵力、内嵌战役、三条想定作战目标、三条关联活动、一条交互规则，并激活该想定。
 */
@Service
public class HuaihaiScenarioFillService {

    private static final String DEMO_ACTIVITY_PREFIX = "淮海战役-";
    private static final String DEMO_RULE_PREFIX = "淮海战役-";

    /** 与前端早期脚本一致的活动名，覆盖时需一并删除 */
    private static final Set<String> HUAIHAI_LEGACY_ACTIVITY_NAMES = Set.of(
            "围歼黄百韬兵团", "围歼黄维兵团", "围歼杜聿明集团"
    );

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private ScenarioActivationService scenarioActivationService;

    @Autowired
    private CampaignService campaignService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private CombatActivityService combatActivityService;

    @Autowired
    private InteractionRuleService interactionRuleService;

    @Autowired
    private CombatActivityRepository combatActivityRepository;

    @Autowired
    private InteractionRuleRepository interactionRuleRepository;

    public Map<String, Object> fillSavedScenario(String scenarioId) {
        if (scenarioId == null || scenarioId.trim().isEmpty()) {
            throw new IllegalArgumentException("想定 id 不能为空");
        }
        ScenarioData sd = scenarioService.getScenarioDataById(scenarioId);
        if (sd == null) {
            throw new IllegalArgumentException("想定不存在: " + scenarioId);
        }

        removePreviousDemoArtifacts(scenarioId);

        sd.setSaveType("CAMPAIGN");
        sd.setCampaign(CampaignDemoData.huaiHaiCampaign());
        sd.setUnits(campaignService.buildHuaiHaiEmbeddedUnits());
        sd.setRedSideName("中国人民解放军");
        sd.setBlueSideName("国民党军");
        if (sd.getName() == null || sd.getName().trim().isEmpty()) {
            sd.setName("淮海战役");
        }
        if (sd.getDescription() == null || sd.getDescription().trim().isEmpty()) {
            sd.setDescription("淮海战役演示想定（兵力与战役文档已自动填充）");
        }
        if (sd.getMaxRounds() <= 0) {
            sd.setMaxRounds(100);
        }

        sd.setObjectives(new ArrayList<>(HuaihaiScenarioDefaults.scenarioCombatObjectives()));

        scenarioService.saveScenarioData(sd);
        scenarioActivationService.activateScenario(scenarioId);

        List<CombatUnit> live = combatUnitService.getUnitsForBattleEngine();
        List<String> eastIds = live.stream()
                .filter(u -> "RED".equals(u.getSide()) && u.getName() != null && u.getName().contains("华东野战军"))
                .map(CombatUnit::getId)
                .collect(Collectors.toList());
        List<String> centralIds = live.stream()
                .filter(u -> "RED".equals(u.getSide()) && u.getName() != null && u.getName().contains("中原野战军"))
                .map(CombatUnit::getId)
                .collect(Collectors.toList());
        List<String> allRedIds = live.stream()
                .filter(u -> "RED".equals(u.getSide()))
                .map(CombatUnit::getId)
                .collect(Collectors.toList());

        CombatActivity act1 = buildHuaiHaiHuangBaiTao(scenarioId, eastIds);
        CombatActivity savedAct = combatActivityService.createActivity(act1);

        combatActivityService.createActivity(buildHuaiHaiHuangWei(scenarioId, centralIds));
        combatActivityService.createActivity(buildHuaiHaiDuYuMing(scenarioId, allRedIds));

        InteractionRule rule = new InteractionRule();
        rule.setName(DEMO_RULE_PREFIX + "协同攻击");
        rule.setType("COORDINATE");
        rule.setDescription("多单位协同接敌时伤害提升（演示规则）");
        rule.setScenarioId(scenarioId);
        rule.setSourceSide("RED");
        rule.setTargetSide("BLUE");
        rule.setEffectType("DAMAGE_BOOST");
        rule.setEffectValue(1.25);
        rule.setMinDistance(0);
        rule.setMaxDistance(1500);
        rule.setStartRound(1);
        rule.setEndRound(50);
        rule.setEnabled(true);
        rule.setPriority(5);

        InteractionRule savedRule = interactionRuleService.addInteractionRule(rule);

        Map<String, Object> out = new HashMap<>();
        out.put("scenarioId", scenarioId);
        out.put("activityId", savedAct.getId());
        out.put("activityName", savedAct.getName());
        out.put("objectiveIds", List.of(
                HuaihaiScenarioDefaults.SCN_OBJ_HUANG_BAITAO,
                HuaihaiScenarioDefaults.SCN_OBJ_HUANG_WEI,
                HuaihaiScenarioDefaults.SCN_OBJ_DU_YUMING));
        out.put("ruleId", savedRule.getId());
        out.put("ruleName", savedRule.getName());
        out.put("message", "已写入淮海兵力与战役文档，并创建活动与规则，想定已激活");
        return out;
    }

    private void removePreviousDemoArtifacts(String scenarioId) {
        for (CombatActivity a : combatActivityRepository.findAll()) {
            if (a == null || !scenarioId.equals(a.getScenarioId()) || a.getName() == null) {
                continue;
            }
            String n = a.getName();
            if (n.startsWith(DEMO_ACTIVITY_PREFIX) || HUAIHAI_LEGACY_ACTIVITY_NAMES.contains(n)) {
                combatActivityRepository.deleteById(a.getId());
            }
        }
        for (InteractionRule r : interactionRuleRepository.findAll()) {
            if (scenarioId.equals(r.getScenarioId()) && r.getName() != null && r.getName().startsWith(DEMO_RULE_PREFIX)) {
                interactionRuleRepository.deleteById(r.getId());
            }
        }
    }

    private CombatActivity buildHuaiHaiHuangBaiTao(String scenarioId, List<String> unitIds) {
        CombatActivity a = baseHuaiHaiActivity(scenarioId, DEMO_ACTIVITY_PREFIX + "围歼黄百韬兵团",
                "华东野战军围歼黄百韬兵团（碾庄方向）",
                unitIds, 1, 10, HuaihaiScenarioDefaults.SCN_OBJ_HUANG_BAITAO);
        a.setMission("华东野战军各纵队向碾庄地区机动，对黄百韬兵团实施合围并歼灭。");
        a.setVictoryCondition("当面黄百韬兵团被歼灭或失去成建制作战能力。");
        a.setFailureCondition("未能在规定回合内完成合围，敌主力突围成功。");
        a.setTargetLatitude(34.45);
        a.setTargetLongitude(117.78);
        a.setTargetRadius(25000.0);
        a.setTargetArea("碾庄—运河一线作战地域");
        a.setCampaignObjectiveLabel("围歼黄百韬兵团");
        List<CombatActivity.ActivityStep> steps = new ArrayList<>();
        steps.add(step(1, "集结", "华东野战军各纵队向碾庄地区集结", "MOVE", "碾庄", 2));
        steps.add(step(2, "包围", "包围黄百韬兵团", "ATTACK", "黄百韬兵团", 3));
        steps.add(step(3, "歼灭", "全歼黄百韬兵团", "ATTACK", "黄百韬兵团", 5));
        a.setSteps(steps);
        return a;
    }

    private CombatActivity buildHuaiHaiHuangWei(String scenarioId, List<String> unitIds) {
        CombatActivity a = baseHuaiHaiActivity(scenarioId, DEMO_ACTIVITY_PREFIX + "围歼黄维兵团",
                "中原野战军围歼黄维兵团（双堆集方向）",
                unitIds, 11, 20, HuaihaiScenarioDefaults.SCN_OBJ_HUANG_WEI);
        a.setMission("阻援、分割并对黄维兵团实施围歼。");
        a.setVictoryCondition("黄维兵团被歼灭或集团投降。");
        a.setFailureCondition("敌援军打通走廊或黄维集团突围成功。");
        a.setTargetLatitude(33.85);
        a.setTargetLongitude(116.55);
        a.setTargetRadius(22000.0);
        a.setTargetArea("双堆集—南坪集作战地域");
        a.setCampaignObjectiveLabel("围歼黄维兵团");
        List<CombatActivity.ActivityStep> steps = new ArrayList<>();
        steps.add(step(1, "阻援", "阻止黄维兵团增援", "DEFEND", "黄维兵团", 2));
        steps.add(step(2, "包围", "包围黄维兵团于双堆集", "ATTACK", "黄维兵团", 3));
        steps.add(step(3, "歼灭", "全歼黄维兵团", "ATTACK", "黄维兵团", 5));
        a.setSteps(steps);
        return a;
    }

    private CombatActivity buildHuaiHaiDuYuMing(String scenarioId, List<String> unitIds) {
        CombatActivity a = baseHuaiHaiActivity(scenarioId, DEMO_ACTIVITY_PREFIX + "围歼杜聿明集团",
                "围歼杜聿明集团（陈官庄方向）",
                unitIds, 21, 30, HuaihaiScenarioDefaults.SCN_OBJ_DU_YUMING);
        a.setMission("追击并围困杜聿明集团于陈官庄，待机歼灭。");
        a.setVictoryCondition("杜聿明集团被歼灭或率部投降。");
        a.setFailureCondition("敌突围成功或长期无法形成决定性包围。");
        a.setTargetLatitude(34.05);
        a.setTargetLongitude(116.45);
        a.setTargetRadius(32000.0);
        a.setTargetArea("陈官庄—青龙集地区");
        a.setCampaignObjectiveLabel("围歼杜聿明集团");
        List<CombatActivity.ActivityStep> steps = new ArrayList<>();
        steps.add(step(1, "追击", "追击杜聿明集团", "MOVE", "陈官庄", 2));
        steps.add(step(2, "包围", "包围杜聿明集团于陈官庄", "ATTACK", "杜聿明集团", 3));
        steps.add(step(3, "歼灭", "全歼杜聿明集团", "ATTACK", "杜聿明集团", 5));
        a.setSteps(steps);
        return a;
    }

    private static CombatActivity baseHuaiHaiActivity(String scenarioId, String name, String description,
                                                      List<String> unitIds, int startRound, int endRound, String objectiveId) {
        CombatActivity activity = new CombatActivity();
        activity.setName(name);
        activity.setType("ATTACK");
        activity.setSide("RED");
        activity.setDescription(description);
        activity.setScenarioId(scenarioId);
        activity.setObjectiveId(objectiveId);
        activity.setUnitIds(unitIds);
        activity.setStartRound(startRound);
        activity.setEndRound(endRound);
        activity.setStatus("PLANNED");
        activity.setCurrentStep(0);
        return activity;
    }

    private static CombatActivity.ActivityStep step(int num, String name, String desc, String action, String target, int rounds) {
        CombatActivity.ActivityStep s = new CombatActivity.ActivityStep();
        s.setStepNumber(num);
        s.setName(name);
        s.setDescription(desc);
        s.setAction(action);
        s.setTarget(target);
        s.setTargetType("AREA");
        s.setRoundDuration(rounds);
        s.setCompleted(false);
        return s;
    }
}
