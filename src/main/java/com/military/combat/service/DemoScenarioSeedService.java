package com.military.combat.service;

import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 提供固定 ID 的演示想定，便于本地/联调时一键恢复可推演态势。
 * 红蓝双方均含：主力驱护、预警/长航时侦察、电子战节点，满足前端信息维度部署校验（按指挥方过滤）。
 */
@Service
public class DemoScenarioSeedService {

    /** 稳定想定 ID：重复调用时复用同一条 Mongo 文档 */
    public static final String DEMO_SCENARIO_ID = "00000000-0000-4000-8000-000000000001";
    public static final String DEMO_RED_UNIT_ID = "00000000-0000-4000-8000-000000000011";
    public static final String DEMO_BLUE_UNIT_ID = "00000000-0000-4000-8000-000000000012";
    public static final String DEMO_OBJECTIVE_ID = "00000000-0000-4000-8000-000000000013";
    public static final String DEMO_RED_AWACS_ID = "00000000-0000-4000-8000-000000000021";
    public static final String DEMO_RED_EW_JET_ID = "00000000-0000-4000-8000-000000000022";
    public static final String DEMO_BLUE_UAV_LONG_ID = "00000000-0000-4000-8000-000000000023";
    public static final String DEMO_BLUE_EW_SHIP_ID = "00000000-0000-4000-8000-000000000024";

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private ScenarioActivationService scenarioActivationService;

    /**
     * 始终以当前模板覆盖保存演示想定（便于升级种子兵力后刷新旧库），再重置会话并激活。
     */
    public ScenarioData ensureDemoScenario() {
        scenarioService.saveScenarioData(buildDemoScenario());
        scenarioActivationService.resetScenarioToInitialState(DEMO_SCENARIO_ID);
        return scenarioService.getScenarioDataById(DEMO_SCENARIO_ID);
    }

    private static ScenarioData buildDemoScenario() {
        ScenarioData sd = new ScenarioData();
        sd.setId(DEMO_SCENARIO_ID);
        sd.setName("【测试】演示想定");
        sd.setDescription("一键载入：红蓝驱护 + 预警/长航时侦察 + 电子战节点，满足建模就绪与信息维度部署校验。");
        sd.setScenarioDomain("SEA");
        sd.setEvaluationProfile("NAVAL_BALANCED");
        sd.setSaveType("TEMPLATE");
        sd.setMaxRounds(50);
        sd.setVictoryCondition("NAVAL_OBJECTIVES");
        sd.setRedSideName("红方");
        sd.setBlueSideName("蓝方");

        List<CombatUnit> units = new ArrayList<>();
        units.add(unitRed());
        units.add(unitBlue());
        units.add(unitRedAwacs());
        units.add(unitRedEwJet());
        units.add(unitBlueUavLong());
        units.add(unitBlueEwShip());
        sd.setUnits(units);

        CombatObjective obj = new CombatObjective();
        obj.setId(DEMO_OBJECTIVE_ID);
        obj.setName("控制关键测试海域");
        obj.setType("CAPTURE");
        obj.setSide("RED");
        obj.setPriority(8);
        obj.setCompleted(false);
        obj.setObjectiveType("AREA_CONTROL");
        obj.setTargetAreaCode("SEA_AREA_DEFAULT");
        obj.setControlThreshold(0.6);
        obj.setDescription("演示用区域控制目标");
        obj.setLatitude(22.8);
        obj.setLongitude(121.5);
        sd.setObjectives(List.of(obj));

        return sd;
    }

    private static CombatUnit unitRed() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_UNIT_ID);
        u.setName("红方测试驱逐舰");
        u.setSide("RED");
        u.setType("DESTROYER");
        u.setMission("区域拒止");
        u.setLatitude(22.85);
        u.setLongitude(121.48);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("DESTROYER");
        return u;
    }

    private static CombatUnit unitBlue() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_UNIT_ID);
        u.setName("蓝方测试护卫舰");
        u.setSide("BLUE");
        u.setType("FRIGATE");
        u.setMission("前沿存在");
        u.setLatitude(22.75);
        u.setLongitude(121.52);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("FRIGATE");
        return u;
    }

    private static CombatUnit unitRedAwacs() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_AWACS_ID);
        u.setName("红方测试预警机");
        u.setSide("RED");
        u.setType("AWACS");
        u.setMission("空情预警");
        u.setLatitude(22.88);
        u.setLongitude(121.46);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("AIR");
        u.setPlatformClass("AWACS");
        return u;
    }

    private static CombatUnit unitRedEwJet() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_EW_JET_ID);
        u.setName("红方测试电子战飞机");
        u.setSide("RED");
        u.setType("EW_JET");
        u.setMission("电磁压制");
        u.setLatitude(22.87);
        u.setLongitude(121.49);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("AIR");
        u.setPlatformClass("EW_JET");
        return u;
    }

    private static CombatUnit unitBlueUavLong() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_UAV_LONG_ID);
        u.setName("蓝方测试长航时侦察无人机");
        u.setSide("BLUE");
        u.setType("UAV_LONG_ENDURANCE");
        u.setMission("广域监视");
        u.setLatitude(22.73);
        u.setLongitude(121.51);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("AIR");
        u.setPlatformClass("UAV_LONG_ENDURANCE");
        return u;
    }

    private static CombatUnit unitBlueEwShip() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_EW_SHIP_ID);
        u.setName("蓝方测试电子侦察船");
        u.setSide("BLUE");
        u.setType("EW_ELINT_SHIP");
        u.setMission("电磁侦察");
        u.setLatitude(22.74);
        u.setLongitude(121.53);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("EW_ELINT_SHIP");
        return u;
    }
}
