package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.ScenarioData;
import com.military.combat.entity.Weapon;
import com.military.combat.repository.CombatUnitRepository;
import com.military.combat.util.NavalDomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CombatUnitService {

    @Autowired
    private CombatUnitRepository repository;

    @Autowired
    @Lazy
    private ScenarioService scenarioService;

    // --- 单位管理 ---    
    public CombatUnit addUnit(CombatUnit unit) {
        NavalDomainValidator.normalizeAndValidateUnit(unit);
        if (unit.getStatus() == null) unit.setStatus("ACTIVE");
        if (unit.getMaxPower() == 0) unit.setMaxPower(unit.getCombatPower());
        if (unit.getAttackRange() == 0) unit.setAttackRange(20);
        if (unit.getSpeed() == 0) unit.setSpeed(10);
        applyAdvancedDefaults(unit);
        return repository.save(unit);
    }
    
    public CombatUnit updateUnit(CombatUnit unit) {
        NavalDomainValidator.normalizeAndValidateUnit(unit);
        if (unit.getId() == null) {
            throw new IllegalArgumentException("单位ID不能为空");
        }
        CombatUnit existing = repository.findById(unit.getId())
            .orElseThrow(() -> new IllegalArgumentException("单位不存在: " + unit.getId()));
        if (unit.getName() != null) existing.setName(unit.getName());
        existing.setCombatPower(unit.getCombatPower());
        existing.setMaxPower(unit.getMaxPower());
        existing.setAttackRange(unit.getAttackRange());
        existing.setSpeed(unit.getSpeed());
        if (unit.getStatus() != null) existing.setStatus(unit.getStatus());
        if (unit.getMission() != null) existing.setMission(unit.getMission());
        if (unit.getWeapons() != null) existing.setWeapons(unit.getWeapons());
        if (unit.getEquipment() != null) existing.setEquipment(unit.getEquipment());
        if (unit.getType() != null) existing.setType(unit.getType());
        if (unit.getSide() != null) existing.setSide(unit.getSide());
        if (unit.getLatitude() != null) existing.setLatitude(unit.getLatitude());
        if (unit.getLongitude() != null) existing.setLongitude(unit.getLongitude());
        existing.setX(unit.getX());
        existing.setY(unit.getY());
        if (unit.getObjectiveId() != null) existing.setObjectiveId(unit.getObjectiveId());
        if (unit.getScenarioId() != null) existing.setScenarioId(unit.getScenarioId());
        if (unit.getDomain() != null) existing.setDomain(unit.getDomain());
        if (unit.getPlatformClass() != null) existing.setPlatformClass(unit.getPlatformClass());
        if (unit.getRole() != null) existing.setRole(unit.getRole());
        if (unit.getParentUnitId() != null) existing.setParentUnitId(unit.getParentUnitId());
        if (unit.getChildUnitIds() != null) existing.setChildUnitIds(unit.getChildUnitIds());
        existing.setSortieCapacity(unit.getSortieCapacity());
        existing.setDeckCapacity(unit.getDeckCapacity());
        existing.setFuelLevel(unit.getFuelLevel());
        existing.setAmmoLevel(unit.getAmmoLevel());
        existing.setReadinessLevel(unit.getReadinessLevel());
        existing.setStealthFactor(unit.getStealthFactor());
        existing.setEcmStrength(unit.getEcmStrength());
        if (unit.getSensors() != null) existing.setSensors(unit.getSensors());
        if (unit.getVesselType() != null) existing.setVesselType(unit.getVesselType());
        if (unit.getNavalTaskType() != null) existing.setNavalTaskType(unit.getNavalTaskType());
        if (unit.getSeaAreaPoint() != null) existing.setSeaAreaPoint(unit.getSeaAreaPoint());
        if (unit.getSupplyStatus() != null) existing.setSupplyStatus(unit.getSupplyStatus());
        if (unit.getDetectionChainStatus() != null) existing.setDetectionChainStatus(unit.getDetectionChainStatus());
        if (unit.getFormationRole() != null) existing.setFormationRole(unit.getFormationRole());
        existing.setAntiAirCapability(unit.getAntiAirCapability());
        existing.setAntiShipCapability(unit.getAntiShipCapability());
        existing.setAntiSubCapability(unit.getAntiSubCapability());
        existing.setSeaControlContribution(unit.getSeaControlContribution());
        return repository.save(existing);
    }
    
    public void deleteUnit(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("单位ID不能为空");
        }
        if (!repository.existsById(id)) {
            throw new IllegalArgumentException("单位不存在: " + id);
        }
        repository.deleteById(id);
    }

    public List<CombatUnit> getAllUnits() {
        return repository.findAll();
    }

    /**
     * 推演引擎与当前战场单位列表：激活想定后严格按 scenarioId 读取。
     */
    public List<CombatUnit> getUnitsForBattleEngine() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            return repository.findAll();
        }
        return repository.findByScenarioId(sid);
    }

    /**
     * 与 {@link #getUnitsForBattleEngine()} 一致，供前端列表与地图展示。
     */
    public List<CombatUnit> getVisibleUnitsForList() {
        return getUnitsForBattleEngine();
    }

    /**
     * 激活想定时：以想定内嵌兵力为唯一真相覆盖 Mongo 战场单位（与想定文档一致）。
     */
    public void applyScenarioDeploymentToLive(ScenarioData sd) {
        if (sd == null || sd.getId() == null) {
            return;
        }
        // 破坏性同步策略：激活新想定即重建战场单位。
        repository.deleteAll();
        List<CombatUnit> units = sd.getUnits();
        if (units == null || units.isEmpty()) {
            return;
        }
        for (CombatUnit unit : units) {
            if (unit == null) {
                continue;
            }
            if (unit.getId() == null || unit.getId().isEmpty()) {
                unit.setId(UUID.randomUUID().toString());
            }
            unit.setScenarioId(sd.getId());
            if (unit.getStatus() == null) unit.setStatus("ACTIVE");
            if (unit.getMaxPower() == 0) unit.setMaxPower(unit.getCombatPower());
            if (unit.getAttackRange() == 0) unit.setAttackRange(20);
            if (unit.getSpeed() == 0) unit.setSpeed(10);
            applyAdvancedDefaults(unit);
            NavalDomainValidator.normalizeAndValidateUnit(unit);
            repository.save(unit);
        }
    }

    /** 清空战场单位集合（用于取消激活/删除当前想定时回收态势）。 */
    public void clearLiveUnits() {
        repository.deleteAll();
    }

    public CombatUnit getUnitById(String id) {
        return repository.findById(id).orElse(null);
    }

    public List<CombatUnit> getUnitsBySide(String side) {
        return getUnitsForBattleEngine().stream()
                .filter(unit -> unit.getSide().equals(side))
                .collect(Collectors.toList());
    }

    public List<CombatUnit> getActiveUnits() {
        return getUnitsForBattleEngine().stream()
                .filter(unit -> "ACTIVE".equals(unit.getStatus()))
                .collect(Collectors.toList());
    }

    // --- 武器装备模板 ---
    public List<Weapon> getWeaponTemplates() {
        List<Weapon> templates = new ArrayList<>();
        templates.add(new Weapon("AK-47", "RIFLE", 25, 300, 0.7, 30, "突击步枪"));
        templates.add(new Weapon("M1A2主炮", "CANNON", 50, 2000, 0.85, 40, "坦克主炮"));
        templates.add(new Weapon("反坦克导弹", "MISSILE", 80, 5000, 0.9, 4, "反坦克导弹"));
        templates.add(new Weapon("狙击步枪", "SNIPER", 60, 1500, 0.95, 10, "高精度狙击步枪"));
        return templates;
    }

    public List<CombatUnit> getAdvancedUnitTemplates() {
        List<CombatUnit> list = new ArrayList<>();

        CombatUnit carrier = new CombatUnit();
        carrier.setName("蓝方航母打击群-核心航母");
        carrier.setSide("BLUE");
        carrier.setType("CARRIER");
        carrier.setDomain("SEA");
        carrier.setPlatformClass("CARRIER");
        carrier.setRole("COMMAND");
        carrier.setCombatPower(1800);
        carrier.setMaxPower(1800);
        carrier.setAttackRange(220000);
        carrier.setSpeed(16);
        carrier.setFuelLevel(100);
        carrier.setAmmoLevel(1200);
        carrier.setReadinessLevel(0.92);
        carrier.setStealthFactor(0.22);
        carrier.setEcmStrength(0.78);
        carrier.setSortieCapacity(48);
        carrier.setDeckCapacity(70);
        carrier.setMission("海域控制与舰载航空兵出动");
        carrier.setWeapons(List.of(
                new Weapon("舰载防空导弹", "SAM", 95, 120000, 0.82, 96, "区域防空"),
                new Weapon("近防炮", "CIWS", 35, 4000, 0.88, 2000, "末端反导/反机")
        ));
        carrier.setSensors(List.of(
                new com.military.combat.entity.Sensor("舰载三坐标雷达", "RADAR", 420000, 260000, 180000, 5, 0.82, 0.02, "远程空海搜索"),
                new com.military.combat.entity.Sensor("ESM电子支援系统", "ELINT", 350000, 220000, 160000, 3, 0.86, 0.03, "电磁态势感知")
        ));
        list.add(carrier);

        CombatUnit recon = new CombatUnit();
        recon.setName("舰载侦察机-01");
        recon.setSide("BLUE");
        recon.setType("RECON_AIRCRAFT");
        recon.setDomain("AIR");
        recon.setPlatformClass("CARRIER_AIRCRAFT");
        recon.setRole("ISR");
        recon.setParentUnitId("CARRIER_TEMPLATE");
        recon.setCombatPower(180);
        recon.setMaxPower(180);
        recon.setAttackRange(2000);
        recon.setSpeed(240);
        recon.setFuelLevel(100);
        recon.setAmmoLevel(20);
        recon.setReadinessLevel(0.88);
        recon.setStealthFactor(0.48);
        recon.setEcmStrength(0.35);
        recon.setSortieCapacity(2);
        recon.setDeckCapacity(1);
        recon.setMission("远程侦察与持续定位");
        recon.setSensors(List.of(
                new com.military.combat.entity.Sensor("合成孔径雷达吊舱", "SAR", 260000, 200000, 130000, 4, 0.72, 0.02, "全天候成像侦察"),
                new com.military.combat.entity.Sensor("光电红外侦察包", "EOIR", 120000, 80000, 55000, 2, 0.58, 0.01, "高分辨率识别")
        ));
        list.add(recon);

        CombatUnit jammer = new CombatUnit();
        jammer.setName("舰载电子战机-01");
        jammer.setSide("BLUE");
        jammer.setType("JAMMER_AIRCRAFT");
        jammer.setDomain("AIR");
        jammer.setPlatformClass("CARRIER_AIRCRAFT");
        jammer.setRole("EW");
        jammer.setParentUnitId("CARRIER_TEMPLATE");
        jammer.setCombatPower(200);
        jammer.setMaxPower(200);
        jammer.setAttackRange(5000);
        jammer.setSpeed(230);
        jammer.setFuelLevel(100);
        jammer.setAmmoLevel(40);
        jammer.setReadinessLevel(0.86);
        jammer.setStealthFactor(0.36);
        jammer.setEcmStrength(0.92);
        jammer.setSortieCapacity(2);
        jammer.setDeckCapacity(1);
        jammer.setMission("压制敌方雷达与通信链路");
        jammer.setSensors(List.of(
                new com.military.combat.entity.Sensor("宽频侦收系统", "ELINT", 220000, 160000, 110000, 2, 0.9, 0.03, "电子侦察与威胁定位")
        ));
        jammer.setWeapons(List.of(
                new Weapon("电子干扰吊舱", "EW_POD", 0, 180000, 0.9, 999, "区域压制干扰")
        ));
        list.add(jammer);

        CombatUnit bomber = new CombatUnit();
        bomber.setName("舰载打击机-01");
        bomber.setSide("BLUE");
        bomber.setType("NAVAL_BOMBER");
        bomber.setDomain("AIR");
        bomber.setPlatformClass("CARRIER_AIRCRAFT");
        bomber.setRole("STRIKE");
        bomber.setParentUnitId("CARRIER_TEMPLATE");
        bomber.setCombatPower(260);
        bomber.setMaxPower(260);
        bomber.setAttackRange(280000);
        bomber.setSpeed(260);
        bomber.setFuelLevel(100);
        bomber.setAmmoLevel(24);
        bomber.setReadinessLevel(0.9);
        bomber.setStealthFactor(0.42);
        bomber.setEcmStrength(0.5);
        bomber.setSortieCapacity(2);
        bomber.setDeckCapacity(1);
        bomber.setMission("对海/对陆精确打击");
        bomber.setWeapons(List.of(
                new Weapon("反舰导弹", "ANTI_SHIP_MISSILE", 180, 260000, 0.83, 6, "超视距反舰"),
                new Weapon("制导炸弹", "GUIDED_BOMB", 120, 45000, 0.87, 10, "对地精确打击")
        ));
        bomber.setSensors(List.of(
                new com.military.combat.entity.Sensor("火控雷达", "RADAR", 160000, 130000, 90000, 2, 0.64, 0.02, "目标捕获与火控")
        ));
        list.add(bomber);

        // --- 陆军重装与陆航 ---
        CombatUnit heavyArmorBrigade = new CombatUnit();
        heavyArmorBrigade.setName("重型合成旅-装甲突击群");
        heavyArmorBrigade.setSide("RED");
        heavyArmorBrigade.setType("ARMORED_BRIGADE");
        heavyArmorBrigade.setDomain("LAND");
        heavyArmorBrigade.setPlatformClass("ARMORED_FORMATION");
        heavyArmorBrigade.setRole("STRIKE");
        heavyArmorBrigade.setCombatPower(1200);
        heavyArmorBrigade.setMaxPower(1200);
        heavyArmorBrigade.setAttackRange(8000);
        heavyArmorBrigade.setSpeed(22);
        heavyArmorBrigade.setFuelLevel(100);
        heavyArmorBrigade.setAmmoLevel(900);
        heavyArmorBrigade.setReadinessLevel(0.9);
        heavyArmorBrigade.setStealthFactor(0.18);
        heavyArmorBrigade.setEcmStrength(0.35);
        heavyArmorBrigade.setMission("地面突破与纵深突击");
        heavyArmorBrigade.setWeapons(List.of(
                new Weapon("125mm坦克炮群", "CANNON", 90, 4500, 0.78, 300, "装甲火力核心"),
                new Weapon("反坦克导弹连", "ATGM", 110, 8000, 0.75, 120, "远距反装甲")
        ));
        heavyArmorBrigade.setSensors(List.of(
                new com.military.combat.entity.Sensor("地面监视雷达", "RADAR", 45000, 30000, 18000, 6, 0.45, 0.04, "地面目标搜索"),
                new com.military.combat.entity.Sensor("前沿光电侦察", "EOIR", 15000, 10000, 7000, 3, 0.35, 0.03, "目视/红外识别")
        ));
        list.add(heavyArmorBrigade);

        CombatUnit armyAviation = new CombatUnit();
        armyAviation.setName("陆航突击直升机营");
        armyAviation.setSide("RED");
        armyAviation.setType("ARMY_AVIATION");
        armyAviation.setDomain("AIR");
        armyAviation.setPlatformClass("HELICOPTER_GROUP");
        armyAviation.setRole("STRIKE");
        armyAviation.setCombatPower(520);
        armyAviation.setMaxPower(520);
        armyAviation.setAttackRange(12000);
        armyAviation.setSpeed(95);
        armyAviation.setFuelLevel(100);
        armyAviation.setAmmoLevel(260);
        armyAviation.setReadinessLevel(0.86);
        armyAviation.setStealthFactor(0.38);
        armyAviation.setEcmStrength(0.44);
        armyAviation.setMission("低空反装甲与机动火力支援");
        armyAviation.setWeapons(List.of(
                new Weapon("空地反坦克导弹", "HEL_ATGM", 130, 10000, 0.8, 64, "对装甲打击"),
                new Weapon("70mm火箭巢", "ROCKET", 45, 6000, 0.58, 220, "面积压制")
        ));
        armyAviation.setSensors(List.of(
                new com.military.combat.entity.Sensor("机载毫米波雷达", "RADAR", 24000, 18000, 12000, 2, 0.58, 0.03, "低空目标捕获"),
                new com.military.combat.entity.Sensor("机载光电瞄准具", "EOIR", 12000, 9000, 7000, 1.5, 0.42, 0.02, "精确识别与测距")
        ));
        list.add(armyAviation);

        // --- 火箭军与防空反导 ---
        CombatUnit rocketForce = new CombatUnit();
        rocketForce.setName("火箭军常规导弹旅");
        rocketForce.setSide("RED");
        rocketForce.setType("ROCKET_FORCE");
        rocketForce.setDomain("LAND");
        rocketForce.setPlatformClass("MISSILE_BRIGADE");
        rocketForce.setRole("STRIKE");
        rocketForce.setCombatPower(900);
        rocketForce.setMaxPower(900);
        rocketForce.setAttackRange(1200000);
        rocketForce.setSpeed(12);
        rocketForce.setFuelLevel(100);
        rocketForce.setAmmoLevel(48);
        rocketForce.setReadinessLevel(0.88);
        rocketForce.setStealthFactor(0.5);
        rocketForce.setEcmStrength(0.62);
        rocketForce.setMission("远程纵深精确打击");
        rocketForce.setWeapons(List.of(
                new Weapon("常规弹道导弹", "SRBM_MRBM", 260, 1200000, 0.86, 32, "纵深高价值目标打击"),
                new Weapon("巡航导弹", "LAND_CRUISE", 180, 1800000, 0.9, 16, "高精度突防打击")
        ));
        rocketForce.setSensors(List.of(
                new com.military.combat.entity.Sensor("导弹旅目标接收链路", "ELINT", 9999999, 9999999, 9999999, 5, 0.92, 0.01, "依托联合侦察体系接收目标")
        ));
        list.add(rocketForce);

        CombatUnit airDefenseBrigade = new CombatUnit();
        airDefenseBrigade.setName("防空反导旅");
        airDefenseBrigade.setSide("RED");
        airDefenseBrigade.setType("AIR_DEFENSE_BRIGADE");
        airDefenseBrigade.setDomain("LAND");
        airDefenseBrigade.setPlatformClass("IADS_NODE");
        airDefenseBrigade.setRole("AIR_DEFENSE");
        airDefenseBrigade.setCombatPower(780);
        airDefenseBrigade.setMaxPower(780);
        airDefenseBrigade.setAttackRange(250000);
        airDefenseBrigade.setSpeed(10);
        airDefenseBrigade.setFuelLevel(100);
        airDefenseBrigade.setAmmoLevel(220);
        airDefenseBrigade.setReadinessLevel(0.91);
        airDefenseBrigade.setStealthFactor(0.28);
        airDefenseBrigade.setEcmStrength(0.67);
        airDefenseBrigade.setMission("区域防空与末端反导拦截");
        airDefenseBrigade.setWeapons(List.of(
                new Weapon("远程防空导弹", "LONG_SAM", 140, 250000, 0.82, 96, "区域防空"),
                new Weapon("近程防空导弹", "SHORT_SAM", 80, 35000, 0.88, 124, "末端补盲")
        ));
        airDefenseBrigade.setSensors(List.of(
                new com.military.combat.entity.Sensor("远程预警雷达", "RADAR", 550000, 360000, 220000, 4, 0.8, 0.02, "空情预警"),
                new com.military.combat.entity.Sensor("反导火控雷达", "RADAR", 420000, 300000, 180000, 2, 0.84, 0.01, "拦截火控")
        ));
        list.add(airDefenseBrigade);

        // --- 海军水面舰艇与潜艇 ---
        CombatUnit destroyer = new CombatUnit();
        destroyer.setName("远海驱逐舰编队");
        destroyer.setSide("BLUE");
        destroyer.setType("DESTROYER");
        destroyer.setDomain("SEA");
        destroyer.setPlatformClass("SURFACE_COMBATANT");
        destroyer.setRole("AIR_DEFENSE");
        destroyer.setCombatPower(760);
        destroyer.setMaxPower(760);
        destroyer.setAttackRange(320000);
        destroyer.setSpeed(18);
        destroyer.setFuelLevel(100);
        destroyer.setAmmoLevel(600);
        destroyer.setReadinessLevel(0.89);
        destroyer.setStealthFactor(0.34);
        destroyer.setEcmStrength(0.73);
        destroyer.setMission("舰队防空与反舰打击");
        destroyer.setWeapons(List.of(
                new Weapon("舰空导弹垂发系统", "NAVAL_SAM", 120, 220000, 0.84, 72, "区域舰队防空"),
                new Weapon("反舰导弹", "AShM", 170, 320000, 0.8, 24, "对海打击")
        ));
        destroyer.setSensors(List.of(
                new com.military.combat.entity.Sensor("有源相控阵雷达", "RADAR", 480000, 330000, 210000, 3, 0.82, 0.02, "全空域监视"),
                new com.military.combat.entity.Sensor("舰载电子侦察系统", "ELINT", 300000, 220000, 150000, 2.5, 0.79, 0.03, "电磁侦察")
        ));
        list.add(destroyer);

        CombatUnit submarine = new CombatUnit();
        submarine.setName("攻击核潜艇");
        submarine.setSide("BLUE");
        submarine.setType("SUBMARINE");
        submarine.setDomain("SEA");
        submarine.setPlatformClass("SUB_SURFACE");
        submarine.setRole("STRIKE");
        submarine.setCombatPower(640);
        submarine.setMaxPower(640);
        submarine.setAttackRange(450000);
        submarine.setSpeed(14);
        submarine.setFuelLevel(100);
        submarine.setAmmoLevel(180);
        submarine.setReadinessLevel(0.87);
        submarine.setStealthFactor(0.88);
        submarine.setEcmStrength(0.55);
        submarine.setMission("隐蔽侦察与对海/对陆导弹突击");
        submarine.setWeapons(List.of(
                new Weapon("潜射巡航导弹", "SLCM", 190, 1500000, 0.9, 18, "远程精确打击"),
                new Weapon("重型鱼雷", "TORPEDO", 160, 60000, 0.76, 32, "反舰反潜")
        ));
        submarine.setSensors(List.of(
                new com.military.combat.entity.Sensor("被动声呐阵列", "SONAR", 180000, 120000, 70000, 4, 0.68, 0.03, "远距被动探测"),
                new com.military.combat.entity.Sensor("拖曳阵声呐", "SONAR", 240000, 150000, 90000, 6, 0.72, 0.02, "低速高灵敏监听")
        ));
        list.add(submarine);

        // --- 空军体系 ---
        CombatUnit aew = new CombatUnit();
        aew.setName("空军预警机中队");
        aew.setSide("RED");
        aew.setType("AEW_AIRCRAFT");
        aew.setDomain("AIR");
        aew.setPlatformClass("AEW");
        aew.setRole("COMMAND");
        aew.setCombatPower(360);
        aew.setMaxPower(360);
        aew.setAttackRange(2000);
        aew.setSpeed(210);
        aew.setFuelLevel(100);
        aew.setAmmoLevel(10);
        aew.setReadinessLevel(0.9);
        aew.setStealthFactor(0.3);
        aew.setEcmStrength(0.63);
        aew.setMission("空情预警与空中指挥引导");
        aew.setSensors(List.of(
                new com.military.combat.entity.Sensor("机载预警雷达", "RADAR", 520000, 380000, 260000, 2, 0.76, 0.02, "大范围空情监视"),
                new com.military.combat.entity.Sensor("数据链枢纽", "ELINT", 9999999, 9999999, 9999999, 1, 0.88, 0.01, "多平台指控协同")
        ));
        list.add(aew);

        CombatUnit uavRecon = new CombatUnit();
        uavRecon.setName("高空长航时无人侦察机");
        uavRecon.setSide("RED");
        uavRecon.setType("UAV_RECON");
        uavRecon.setDomain("AIR");
        uavRecon.setPlatformClass("UAV");
        uavRecon.setRole("ISR");
        uavRecon.setCombatPower(140);
        uavRecon.setMaxPower(140);
        uavRecon.setAttackRange(3000);
        uavRecon.setSpeed(170);
        uavRecon.setFuelLevel(100);
        uavRecon.setAmmoLevel(8);
        uavRecon.setReadinessLevel(0.93);
        uavRecon.setStealthFactor(0.62);
        uavRecon.setEcmStrength(0.4);
        uavRecon.setMission("持续侦察与实时战场监视");
        uavRecon.setSensors(List.of(
                new com.military.combat.entity.Sensor("广域成像雷达", "SAR", 300000, 220000, 150000, 2.5, 0.7, 0.015, "全天候广域侦察"),
                new com.military.combat.entity.Sensor("高倍率光电吊舱", "EOIR", 160000, 120000, 80000, 1.5, 0.5, 0.01, "持续识别确认")
        ));
        list.add(uavRecon);

        return list;
    }

    public List<String> getAdvancedGroupKeys() {
        return List.of("CARRIER_STRIKE_GROUP", "LAND_ROCKET_ASSAULT_GROUP");
    }

    public List<CombatUnit> deployAdvancedGroup(String groupKey) {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            throw new IllegalStateException("请先激活想定后再部署高级编组");
        }
        List<CombatUnit> templates = getAdvancedUnitTemplates();
        List<CombatUnit> selected = new ArrayList<>();
        if ("CARRIER_STRIKE_GROUP".equals(groupKey)) {
            selected.addAll(filterTypes(templates, List.of("CARRIER", "DESTROYER", "RECON_AIRCRAFT", "JAMMER_AIRCRAFT", "NAVAL_BOMBER")));
        } else if ("LAND_ROCKET_ASSAULT_GROUP".equals(groupKey)) {
            selected.addAll(filterTypes(templates, List.of("ARMORED_BRIGADE", "ARMY_AVIATION", "ROCKET_FORCE", "AIR_DEFENSE_BRIGADE", "UAV_RECON")));
        } else {
            throw new IllegalArgumentException("未知编组: " + groupKey);
        }

        Map<String, String> parentMapping = new HashMap<>();
        List<CombatUnit> out = new ArrayList<>();
        double baseLat = "CARRIER_STRIKE_GROUP".equals(groupKey) ? 30.2 : 36.8;
        double baseLng = "CARRIER_STRIKE_GROUP".equals(groupKey) ? 122.4 : 112.3;
        int idx = 0;
        for (CombatUnit t : selected) {
            CombatUnit c = cloneTemplate(t);
            c.setId(UUID.randomUUID().toString());
            c.setScenarioId(sid);
            c.setLatitude(baseLat + (idx * 0.05));
            c.setLongitude(baseLng + (idx * 0.05));
            c.setStatus("ACTIVE");
            if (c.getType() != null && ("CARRIER".equals(c.getType()) || "ARMORED_BRIGADE".equals(c.getType()))) {
                parentMapping.put(c.getType(), c.getId());
            }
            idx++;
            out.add(repository.save(c));
        }
        // 回填子单元隶属
        for (CombatUnit u : out) {
            if ("CARRIER_AIRCRAFT".equals(u.getPlatformClass())) {
                String pid = parentMapping.get("CARRIER");
                if (pid != null) {
                    u.setParentUnitId(pid);
                    repository.save(u);
                }
            }
        }
        return out;
    }

    private List<CombatUnit> filterTypes(List<CombatUnit> templates, List<String> types) {
        return templates.stream()
                .filter(u -> u != null && u.getType() != null && types.contains(u.getType()))
                .collect(Collectors.toList());
    }

    private CombatUnit cloneTemplate(CombatUnit t) {
        CombatUnit c = new CombatUnit();
        c.setName(t.getName());
        c.setSide(t.getSide());
        c.setType(t.getType());
        c.setCombatPower(t.getCombatPower());
        c.setMaxPower(t.getMaxPower());
        c.setStatus(t.getStatus());
        c.setX(t.getX());
        c.setY(t.getY());
        c.setAttackRange(t.getAttackRange());
        c.setSpeed(t.getSpeed());
        c.setWeapons(t.getWeapons());
        c.setEquipment(t.getEquipment());
        c.setMission(t.getMission());
        c.setObjectiveId(t.getObjectiveId());
        c.setDomain(t.getDomain());
        c.setPlatformClass(t.getPlatformClass());
        c.setRole(t.getRole());
        c.setParentUnitId(t.getParentUnitId());
        c.setChildUnitIds(t.getChildUnitIds());
        c.setSortieCapacity(t.getSortieCapacity());
        c.setDeckCapacity(t.getDeckCapacity());
        c.setFuelLevel(t.getFuelLevel());
        c.setAmmoLevel(t.getAmmoLevel());
        c.setReadinessLevel(t.getReadinessLevel());
        c.setStealthFactor(t.getStealthFactor());
        c.setEcmStrength(t.getEcmStrength());
        c.setSensors(t.getSensors());
        c.setVesselType(t.getVesselType());
        c.setNavalTaskType(t.getNavalTaskType());
        c.setSeaAreaPoint(t.getSeaAreaPoint());
        c.setSupplyStatus(t.getSupplyStatus());
        c.setDetectionChainStatus(t.getDetectionChainStatus());
        c.setFormationRole(t.getFormationRole());
        c.setAntiAirCapability(t.getAntiAirCapability());
        c.setAntiShipCapability(t.getAntiShipCapability());
        c.setAntiSubCapability(t.getAntiSubCapability());
        c.setSeaControlContribution(t.getSeaControlContribution());
        return c;
    }

    private void applyAdvancedDefaults(CombatUnit unit) {
        if (unit.getDomain() == null) unit.setDomain("LAND");
        if (unit.getPlatformClass() == null) unit.setPlatformClass(unit.getType());
        if (unit.getRole() == null) unit.setRole("STRIKE");
        if (unit.getFuelLevel() == 0) unit.setFuelLevel(100);
        if (unit.getAmmoLevel() == 0) unit.setAmmoLevel(100);
        if (unit.getReadinessLevel() == 0) unit.setReadinessLevel(0.85);
        if (unit.getStealthFactor() == 0) unit.setStealthFactor(0.2);
        if (unit.getEcmStrength() == 0) unit.setEcmStrength(0.2);
        if (unit.getSortieCapacity() == 0) unit.setSortieCapacity(1);
        if (unit.getDeckCapacity() == 0) unit.setDeckCapacity(1);
        if (unit.getDomain() != null && "SEA".equalsIgnoreCase(unit.getDomain())) {
            if (unit.getSupplyStatus() == null) unit.setSupplyStatus("ADEQUATE");
            if (unit.getDetectionChainStatus() == null) unit.setDetectionChainStatus("OPEN");
            if (unit.getFormationRole() == null) unit.setFormationRole("CORE");
        }
    }
}
