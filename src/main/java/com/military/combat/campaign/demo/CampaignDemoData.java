package com.military.combat.campaign.demo;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CampaignEvent;
import com.military.combat.entity.CampaignObjective;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示用历史战役数据（淮海 / 抗美援朝），与业务核心「自定义战役 + 想定」解耦。
 * 正式推演以想定内嵌 {@link Campaign} 或 {@code POST /combat/campaign/custom} 为准。
 */
public final class CampaignDemoData {

    private CampaignDemoData() {
    }

    public static Campaign huaiHaiCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("huaihai");
        campaign.setName("淮海战役");
        campaign.setDescription("淮海战役是中国人民解放军与国民党军之间的战略决战，发生于1948年11月至1949年1月，是解放战争中的三大战役之一。");
        campaign.setStartDate("1948-11-06");
        campaign.setEndDate("1949-01-10");

        List<String> phases = new ArrayList<>();
        phases.add("第一阶段：围歼黄百韬兵团");
        phases.add("第二阶段：围歼黄维兵团");
        phases.add("第三阶段：围歼杜聿明集团");
        campaign.setPhases(phases);

        Map<String, String> sides = new HashMap<>();
        sides.put("RED", "中国人民解放军");
        sides.put("BLUE", "国民党军");
        campaign.setSides(sides);

        List<CampaignEvent> events = new ArrayList<>();
        CampaignEvent event1 = new CampaignEvent();
        event1.setId("event1");
        event1.setName("黄百韬兵团被围");
        event1.setDescription("解放军华东野战军包围黄百韬兵团于碾庄地区");
        event1.setPhase("第一阶段：围歼黄百韬兵团");
        // 合法 DSL：蓝方单位进入碾庄附近矩形区域（示意坐标，可随想定微调）
        event1.setTriggerCondition("UNIT_IN_AREA:BLUE:34.35:34.60:117.60:117.95");
        Map<String, Object> effects1 = new HashMap<>();
        effects1.put("message", "黄百韬兵团被解放军包围在碾庄，陷入绝境");
        event1.setEffects(effects1);
        events.add(event1);

        CampaignEvent event2 = new CampaignEvent();
        event2.setId("event2");
        event2.setName("黄维兵团被围");
        event2.setDescription("解放军中原野战军包围黄维兵团于双堆集地区");
        event2.setPhase("第二阶段：围歼黄维兵团");
        event2.setTriggerCondition("UNIT_IN_AREA:BLUE:33.75:33.95:116.45:116.75");
        Map<String, Object> effects2 = new HashMap<>();
        effects2.put("message", "黄维兵团被解放军包围在双堆集，突围失败");
        event2.setEffects(effects2);
        events.add(event2);

        CampaignEvent event3 = new CampaignEvent();
        event3.setId("event3");
        event3.setName("杜聿明集团被围");
        event3.setDescription("解放军包围杜聿明集团于陈官庄地区");
        event3.setPhase("第三阶段：围歼杜聿明集团");
        event3.setTriggerCondition("UNIT_IN_AREA:BLUE:33.95:34.15:116.35:116.55");
        Map<String, Object> effects3 = new HashMap<>();
        effects3.put("message", "杜聿明集团被解放军包围在陈官庄，粮弹耗尽");
        event3.setEffects(effects3);
        events.add(event3);
        campaign.setEvents(events);

        List<CampaignObjective> objectives = new ArrayList<>();
        CampaignObjective obj1 = new CampaignObjective();
        obj1.setId("obj1");
        obj1.setName("围歼黄百韬兵团");
        obj1.setDescription("全歼国民党军黄百韬兵团");
        obj1.setSide("RED");
        obj1.setPhase("第一阶段：围歼黄百韬兵团");
        obj1.setType("DESTROY");
        // 与兵力名称包含关系匹配：当名称含该关键字的蓝方单位全部被歼灭时视为达成
        obj1.setTargetId("name:黄百韬兵团");
        obj1.setPriority(1);
        objectives.add(obj1);

        CampaignObjective obj2 = new CampaignObjective();
        obj2.setId("obj2");
        obj2.setName("围歼黄维兵团");
        obj2.setDescription("全歼国民党军黄维兵团");
        obj2.setSide("RED");
        obj2.setPhase("第二阶段：围歼黄维兵团");
        obj2.setType("DESTROY");
        obj2.setTargetId("name:黄维兵团");
        obj2.setPriority(1);
        objectives.add(obj2);

        CampaignObjective obj3 = new CampaignObjective();
        obj3.setId("obj3");
        obj3.setName("围歼杜聿明集团");
        obj3.setDescription("全歼国民党军杜聿明集团");
        obj3.setSide("RED");
        obj3.setPhase("第三阶段：围歼杜聿明集团");
        obj3.setType("DESTROY");
        obj3.setTargetId("name:杜聿明集团");
        obj3.setPriority(1);
        objectives.add(obj3);
        campaign.setObjectives(objectives);

        Map<String, Object> terrainSettings = new HashMap<>();
        terrainSettings.put("center", new double[]{34.2672, 117.1836});
        terrainSettings.put("zoom", 10);
        terrainSettings.put("terrainTypes", new String[]{"平原", "河流", "村庄", "铁路"});
        campaign.setTerrainSettings(terrainSettings);

        Map<String, Object> rules = new HashMap<>();
        rules.put("winterCombat", true);
        rules.put("supplyLimit", true);
        rules.put("riverCrossing", true);
        campaign.setRules(rules);
        return campaign;
    }

    public static Campaign koreanWarCampaign() {
        Campaign campaign = new Campaign();
        campaign.setId("korean");
        campaign.setName("抗美援朝战争");
        campaign.setDescription("抗美援朝战争是中国人民志愿军与联合国军之间的战争，发生于1950年10月至1953年7月，是新中国成立后的第一场对外战争。");
        campaign.setStartDate("1950-10-19");
        campaign.setEndDate("1953-07-27");

        List<String> phases = new ArrayList<>();
        phases.add("第一阶段：志愿军入朝作战");
        phases.add("第二阶段：五次战役");
        phases.add("第三阶段：阵地战与停战谈判");
        campaign.setPhases(phases);

        Map<String, String> sides = new HashMap<>();
        sides.put("RED", "中国人民志愿军");
        sides.put("BLUE", "联合国军");
        campaign.setSides(sides);

        List<CampaignEvent> events = new ArrayList<>();
        CampaignEvent event1 = new CampaignEvent();
        event1.setId("event1");
        event1.setName("志愿军入朝");
        event1.setDescription("中国人民志愿军跨过鸭绿江，进入朝鲜作战");
        event1.setPhase("第一阶段：志愿军入朝作战");
        event1.setTriggerCondition("志愿军进入朝鲜境内");
        Map<String, Object> effects1 = new HashMap<>();
        effects1.put("message", "中国人民志愿军正式入朝作战，拉开了抗美援朝战争的序幕");
        event1.setEffects(effects1);
        events.add(event1);

        CampaignEvent event2 = new CampaignEvent();
        event2.setId("event2");
        event2.setName("第一次战役");
        event2.setDescription("志愿军在云山地区重创美军骑兵第一师");
        event2.setPhase("第二阶段：五次战役");
        event2.setTriggerCondition("志愿军与美军在云山地区接触");
        Map<String, Object> effects2 = new HashMap<>();
        effects2.put("message", "志愿军在云山战役中重创美军，取得入朝首胜");
        event2.setEffects(effects2);
        events.add(event2);

        CampaignEvent event3 = new CampaignEvent();
        event3.setId("event3");
        event3.setName("上甘岭战役");
        event3.setDescription("志愿军在上甘岭地区与美军展开激烈争夺战");
        event3.setPhase("第三阶段：阵地战与停战谈判");
        event3.setTriggerCondition("美军发起对上甘岭的进攻");
        Map<String, Object> effects3 = new HashMap<>();
        effects3.put("message", "上甘岭战役爆发，志愿军与美军展开激烈争夺战");
        event3.setEffects(effects3);
        events.add(event3);
        campaign.setEvents(events);

        List<CampaignObjective> objectives = new ArrayList<>();
        CampaignObjective obj1 = new CampaignObjective();
        obj1.setId("obj1");
        obj1.setName("保卫朝鲜北部");
        obj1.setDescription("阻止联合国军北进，保卫朝鲜北部地区");
        obj1.setSide("RED");
        obj1.setPhase("第一阶段：志愿军入朝作战");
        obj1.setType("DEFEND");
        obj1.setTargetId("north_korea");
        obj1.setPriority(1);
        objectives.add(obj1);

        CampaignObjective obj2 = new CampaignObjective();
        obj2.setId("obj2");
        obj2.setName("推进到三八线");
        obj2.setDescription("将联合国军击退到三八线以南");
        obj2.setSide("RED");
        obj2.setPhase("第二阶段：五次战役");
        obj2.setType("CAPTURE");
        obj2.setTargetId("38th_parallel");
        obj2.setPriority(1);
        objectives.add(obj2);

        CampaignObjective obj3 = new CampaignObjective();
        obj3.setId("obj3");
        obj3.setName("坚守上甘岭");
        obj3.setDescription("坚守上甘岭阵地，粉碎美军进攻");
        obj3.setSide("RED");
        obj3.setPhase("第三阶段：阵地战与停战谈判");
        obj3.setType("DEFEND");
        obj3.setTargetId("sanggamryeong");
        obj3.setPriority(1);
        objectives.add(obj3);

        campaign.setObjectives(objectives);

        Map<String, Object> terrainSettings = new HashMap<>();
        terrainSettings.put("center", new double[]{39.0334, 125.7543});
        terrainSettings.put("zoom", 9);
        terrainSettings.put("terrainTypes", new String[]{"山地", "平原", "河流", "森林"});
        campaign.setTerrainSettings(terrainSettings);

        Map<String, Object> rules = new HashMap<>();
        rules.put("winterCombat", true);
        rules.put("mountainCombat", true);
        rules.put("airSupport", true);
        campaign.setRules(rules);
        return campaign;
    }
}
