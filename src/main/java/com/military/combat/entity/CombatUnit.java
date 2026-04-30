package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 作战单位模型
 */
@Data
@Document(collection = "combat_units")
public class CombatUnit {

    @Id
    private String id;

    private String name;        // 部队名称
    private String side;        // 阵营 (RED / BLUE)
    private String type;        // 类型 (TANK, INFANTRY, AIR)

    private int combatPower;    // 当前血量


    private int maxPower;       // 最大血量 (用于计算血条百分比)


    private String status;      // 状态 (ACTIVE, DESTROYED)

    // --- 运动与战斗属性 ---
    private double x;           // X坐标（百分比，兼容旧系统）
    private double y;           // Y坐标（百分比，兼容旧系统）
    private Double latitude;    // 纬度（真实地理坐标）
    private Double longitude;   // 经度（真实地理坐标）
    private double attackRange; // 攻击距离（米）
    private double speed;       // 移动速度（米/秒）
    
    // --- 武器装备信息 ---
    private java.util.List<Weapon> weapons;  // 装备的武器列表
    private String equipment;                // 装备描述
    
    // --- 作战任务信息 ---
    private String mission;                  // 任务描述
    private String objectiveId;              // 关联的作战目标ID

    /** 所属想定 ID（与 {@link com.military.combat.entity.ScenarioData} 一致；激活带兵力部署的想定时写入） */
    private String scenarioId;

    // --- 现代化平台扩展 ---
    private String domain;                   // LAND/SEA/AIR/SPACE/ELECTROMAGNETIC
    private String platformClass;            // CARRIER/DESTROYER/FIGHTER/AEW/UAV/JAMMER/BOMBER...
    private String role;                     // ISR/STRIKE/EW/AIR_DEFENSE/COMMAND/SUPPORT
    private String parentUnitId;             // 上级平台（如舰载机所属航母）
    private java.util.List<String> childUnitIds; // 下属单元（如航母挂载机群）
    private int sortieCapacity;              // 最大架次/出动能力
    private int deckCapacity;                // 甲板或舱位容量
    private double fuelLevel;                // 当前燃油百分比（0-100）
    private int ammoLevel;                   // 当前弹药库存（抽象值）
    private double readinessLevel;           // 可用率（0-1）
    private double stealthFactor;            // 隐身系数（0-1，越高越隐蔽）
    private double ecmStrength;              // 电子对抗能力（0-1）
    private java.util.List<Sensor> sensors;  // 传感器挂载

    // --- 海上域扩展（字符串存储，值域见 NavalDomainEnums） ---
    private String vesselType;               // CARRIER/DESTROYER/SUBMARINE...
    private String navalTaskType;            // SEA_CONTROL/ESCORT/BLOCKADE...
    private String seaAreaPoint;             // 海域点位标识（如 EAST_CHOKE_POINT）
    private String supplyStatus;             // FULL/ADEQUATE/LOW/CRITICAL/EXHAUSTED
    private String detectionChainStatus;     // OPEN/DEGRADED/JAMMED/LOST
    private String formationRole;            // SCREEN/CORE/PICKET/LOGISTICS
    private double antiAirCapability;        // 编队防空能力（0-1）
    private double antiShipCapability;       // 反舰打击能力（0-1）
    private double antiSubCapability;        // 反潜能力（0-1）
    private double seaControlContribution;   // 对制海指数贡献（0-1）
}