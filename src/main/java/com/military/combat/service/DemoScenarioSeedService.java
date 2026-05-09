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
 * 兵力样本较多（水面/水下/空中/岸基），便于前端部署列表与多回合推演。
 */
@Service
public class DemoScenarioSeedService {

    /** 将红方集群从「近距离交会」平移到西南侧远距部署（相对原样本坐标）。 */
    private static final double RED_CLUSTER_D_LAT = 21.15 - 22.86;
    private static final double RED_CLUSTER_D_LON = 118.75 - 121.47;

    /** 将蓝方集群平移到东北侧远距部署（相对原样本坐标）。 */
    private static final double BLUE_CLUSTER_D_LAT = 24.85 - 22.76;
    private static final double BLUE_CLUSTER_D_LON = 123.25 - 121.51;

    /** 稳定想定 ID：重复调用时复用同一条 Mongo 文档 */
    public static final String DEMO_SCENARIO_ID = "00000000-0000-4000-8000-000000000001";
    public static final String DEMO_RED_UNIT_ID = "00000000-0000-4000-8000-000000000011";
    public static final String DEMO_BLUE_UNIT_ID = "00000000-0000-4000-8000-000000000012";
    public static final String DEMO_OBJECTIVE_ID = "00000000-0000-4000-8000-000000000013";
    public static final String DEMO_OBJECTIVE_2_ID = "00000000-0000-4000-8000-000000000015";
    public static final String DEMO_RED_AWACS_ID = "00000000-0000-4000-8000-000000000021";
    public static final String DEMO_RED_EW_JET_ID = "00000000-0000-4000-8000-000000000022";
    public static final String DEMO_BLUE_UAV_LONG_ID = "00000000-0000-4000-8000-000000000023";
    public static final String DEMO_BLUE_EW_SHIP_ID = "00000000-0000-4000-8000-000000000024";
    public static final String DEMO_RED_FRIGATE_2_ID = "00000000-0000-4000-8000-000000000031";
    public static final String DEMO_RED_SUB_ID = "00000000-0000-4000-8000-000000000032";
    public static final String DEMO_RED_SUPPLY_ID = "00000000-0000-4000-8000-000000000033";
    public static final String DEMO_RED_UAV_ID = "00000000-0000-4000-8000-000000000034";
    public static final String DEMO_RED_CARRIER_ID = "00000000-0000-4000-8000-000000000035";
    public static final String DEMO_BLUE_DD_ID = "00000000-0000-4000-8000-000000000036";
    public static final String DEMO_BLUE_FRIGATE_2_ID = "00000000-0000-4000-8000-000000000037";
    public static final String DEMO_BLUE_SUB_ID = "00000000-0000-4000-8000-000000000038";
    public static final String DEMO_BLUE_AWACS_ID = "00000000-0000-4000-8000-000000000039";
    public static final String DEMO_BLUE_EW_JET_ID = "00000000-0000-4000-8000-000000000040";
    public static final String DEMO_BLUE_SUPPLY_ID = "00000000-0000-4000-8000-000000000041";
    public static final String DEMO_RED_SHORE_RADAR_ID = "00000000-0000-4000-8000-000000000042";
    public static final String DEMO_BLUE_SHORE_RADAR_ID = "00000000-0000-4000-8000-000000000043";

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
        sd.setDescription("多样本兵力（约 18 个平台）+ 双作战目标；红蓝初始相距数百公里，便于逐步进入发现阶段。maxRounds=120。");
        sd.setScenarioDomain("SEA");
        sd.setEvaluationProfile("NAVAL_BALANCED");
        sd.setSaveType("TEMPLATE");
        sd.setMaxRounds(120);
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
        units.add(unitRedFrigate2());
        units.add(unitRedSub());
        units.add(unitRedSupply());
        units.add(unitRedUavRecon());
        units.add(unitRedCarrier());
        units.add(unitBlueDestroyer());
        units.add(unitBlueFrigate2());
        units.add(unitBlueSub());
        units.add(unitBlueAwacs());
        units.add(unitBlueEwJet());
        units.add(unitBlueSupply());
        units.add(unitRedShoreRadar());
        units.add(unitBlueShoreRadar());
        for (CombatUnit u : units) {
            spreadDemoFormationWide(u);
        }
        sd.setUnits(units);

        CombatObjective obj = new CombatObjective();
        obj.setId(DEMO_OBJECTIVE_ID);
        obj.setName("控制关键测试海域（红方）");
        obj.setType("CAPTURE");
        obj.setSide("RED");
        obj.setPriority(9);
        obj.setCompleted(false);
        obj.setObjectiveType("AREA_CONTROL");
        obj.setTargetAreaCode("SEA_AREA_DEFAULT");
        obj.setControlThreshold(0.55);
        obj.setDescription("红方区域控制：夺取并保持中部测试海区。");
        obj.setLatitude(23.0);
        obj.setLongitude(121.02);
        CombatObjective obj2 = new CombatObjective();
        obj2.setId(DEMO_OBJECTIVE_2_ID);
        obj2.setName("前沿据点防御（蓝方）");
        obj2.setType("DEFEND");
        obj2.setSide("BLUE");
        obj2.setPriority(7);
        obj2.setCompleted(false);
        obj2.setObjectiveType("AREA_CONTROL");
        obj2.setTargetAreaCode("SEA_AREA_BLUE_FOP");
        obj2.setControlThreshold(0.5);
        obj2.setDescription("蓝方前沿存在区：阻滞红方南下。");
        obj2.setLatitude(23.06);
        obj2.setLongitude(121.1);
        sd.setObjectives(List.of(obj, obj2));

        return sd;
    }

    private static void spreadDemoFormationWide(CombatUnit u) {
        if (u == null || u.getLatitude() == null || u.getLongitude() == null) {
            return;
        }
        if ("RED".equals(u.getSide())) {
            u.setLatitude(u.getLatitude() + RED_CLUSTER_D_LAT);
            u.setLongitude(u.getLongitude() + RED_CLUSTER_D_LON);
        } else if ("BLUE".equals(u.getSide())) {
            u.setLatitude(u.getLatitude() + BLUE_CLUSTER_D_LAT);
            u.setLongitude(u.getLongitude() + BLUE_CLUSTER_D_LON);
        }
    }

    private static CombatUnit unitRed() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_UNIT_ID);
        u.setName("红方测试驱逐舰-1");
        u.setSide("RED");
        u.setType("DESTROYER");
        u.setMission("区域拒止");
        u.setLatitude(22.86);
        u.setLongitude(121.47);
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
        u.setName("蓝方测试护卫舰-1");
        u.setSide("BLUE");
        u.setType("FRIGATE");
        u.setMission("前沿存在");
        u.setLatitude(22.76);
        u.setLongitude(121.51);
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
        u.setLatitude(22.9);
        u.setLongitude(121.42);
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
        u.setLatitude(22.89);
        u.setLongitude(121.45);
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
        u.setLatitude(22.68);
        u.setLongitude(121.54);
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
        u.setLatitude(22.72);
        u.setLongitude(121.58);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("EW_ELINT_SHIP");
        return u;
    }

    private static CombatUnit unitRedFrigate2() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_FRIGATE_2_ID);
        u.setName("红方测试护卫舰-2");
        u.setSide("RED");
        u.setType("FRIGATE");
        u.setMission("反潜屏护");
        u.setLatitude(22.84);
        u.setLongitude(121.5);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("FRIGATE");
        return u;
    }

    private static CombatUnit unitRedSub() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_SUB_ID);
        u.setName("红方测试潜艇");
        u.setSide("RED");
        u.setType("SUBMARINE");
        u.setMission("伏击巡逻");
        u.setLatitude(22.78);
        u.setLongitude(121.4);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("SUBMARINE");
        u.setVesselType("SUBMARINE");
        return u;
    }

    private static CombatUnit unitRedSupply() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_SUPPLY_ID);
        u.setName("红方测试补给舰");
        u.setSide("RED");
        u.setType("SUPPLY_SHIP");
        u.setMission("编队补给");
        u.setLatitude(22.83);
        u.setLongitude(121.39);
        u.setStatus("ACTIVE");
        u.setCombatPower(80);
        u.setMaxPower(80);
        u.setDomain("SEA");
        u.setPlatformClass("SUPPLY_SHIP");
        return u;
    }

    private static CombatUnit unitRedUavRecon() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_UAV_ID);
        u.setName("红方测试侦察无人机");
        u.setSide("RED");
        u.setType("UAV_RECON");
        u.setMission("目标指示");
        u.setLatitude(22.88);
        u.setLongitude(121.52);
        u.setStatus("ACTIVE");
        u.setCombatPower(60);
        u.setMaxPower(60);
        u.setDomain("AIR");
        u.setPlatformClass("UAV_RECON");
        return u;
    }

    private static CombatUnit unitRedCarrier() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_CARRIER_ID);
        u.setName("红方测试航母");
        u.setSide("RED");
        u.setType("CARRIER");
        u.setMission("编队核心");
        u.setLatitude(22.81);
        u.setLongitude(121.36);
        u.setStatus("ACTIVE");
        u.setCombatPower(120);
        u.setMaxPower(120);
        u.setDomain("SEA");
        u.setPlatformClass("CARRIER");
        u.setVesselType("CARRIER");
        return u;
    }

    private static CombatUnit unitBlueDestroyer() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_DD_ID);
        u.setName("蓝方测试驱逐舰");
        u.setSide("BLUE");
        u.setType("DESTROYER");
        u.setMission("防空指挥");
        u.setLatitude(22.74);
        u.setLongitude(121.49);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("DESTROYER");
        return u;
    }

    private static CombatUnit unitBlueFrigate2() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_FRIGATE_2_ID);
        u.setName("蓝方测试护卫舰-2");
        u.setSide("BLUE");
        u.setType("FRIGATE");
        u.setMission("巡逻警戒");
        u.setLatitude(22.71);
        u.setLongitude(121.53);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("FRIGATE");
        return u;
    }

    private static CombatUnit unitBlueSub() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_SUB_ID);
        u.setName("蓝方测试潜艇");
        u.setSide("BLUE");
        u.setType("SUBMARINE");
        u.setMission("水下伏击");
        u.setLatitude(22.69);
        u.setLongitude(121.47);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        u.setPlatformClass("SUBMARINE");
        u.setVesselType("SUBMARINE");
        return u;
    }

    private static CombatUnit unitBlueAwacs() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_AWACS_ID);
        u.setName("蓝方测试预警机");
        u.setSide("BLUE");
        u.setType("AWACS");
        u.setMission("空情掌握");
        u.setLatitude(22.66);
        u.setLongitude(121.5);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("AIR");
        u.setPlatformClass("AWACS");
        return u;
    }

    private static CombatUnit unitBlueEwJet() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_EW_JET_ID);
        u.setName("蓝方测试电子战飞机");
        u.setSide("BLUE");
        u.setType("EW_JET");
        u.setMission("压制敌雷达");
        u.setLatitude(22.67);
        u.setLongitude(121.57);
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("AIR");
        u.setPlatformClass("EW_JET");
        return u;
    }

    private static CombatUnit unitBlueSupply() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_SUPPLY_ID);
        u.setName("蓝方测试补给舰");
        u.setSide("BLUE");
        u.setType("SUPPLY_SHIP");
        u.setMission("海上补给");
        u.setLatitude(22.73);
        u.setLongitude(121.6);
        u.setStatus("ACTIVE");
        u.setCombatPower(80);
        u.setMaxPower(80);
        u.setDomain("SEA");
        u.setPlatformClass("SUPPLY_SHIP");
        return u;
    }

    private static CombatUnit unitRedShoreRadar() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_RED_SHORE_RADAR_ID);
        u.setName("红方岸基雷达站（样本）");
        u.setSide("RED");
        u.setType("SHORE_RADAR");
        u.setMission("对海警戒");
        u.setLatitude(22.92);
        u.setLongitude(121.4);
        u.setStatus("ACTIVE");
        u.setCombatPower(40);
        u.setMaxPower(40);
        u.setDomain("SEA");
        u.setPlatformClass("SHORE_RADAR");
        return u;
    }

    private static CombatUnit unitBlueShoreRadar() {
        CombatUnit u = new CombatUnit();
        u.setId(DEMO_BLUE_SHORE_RADAR_ID);
        u.setName("蓝方岸基雷达站（样本）");
        u.setSide("BLUE");
        u.setType("SHORE_RADAR");
        u.setMission("对空对海警戒");
        u.setLatitude(22.64);
        u.setLongitude(121.62);
        u.setStatus("ACTIVE");
        u.setCombatPower(40);
        u.setMaxPower(40);
        u.setDomain("SEA");
        u.setPlatformClass("SHORE_RADAR");
        return u;
    }
}
