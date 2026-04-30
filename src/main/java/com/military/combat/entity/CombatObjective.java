package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作战目标实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CombatObjective {
    private String id;
    private String name;           // 目标名称
    private String type;           // 目标类型 (CAPTURE-占领, DESTROY-摧毁, DEFEND-防守)
    private String side;            // 所属阵营 (RED/BLUE)
    private Double latitude;       // 目标位置纬度
    private Double longitude;       // 目标位置经度
    private double x;              // 百分比坐标X
    private double y;              // 百分比坐标Y
    private String description;     // 目标描述
    private int priority;           // 优先级 (1-10)
    private boolean completed;      // 是否完成
    /** 仅 API 传参：写入想定文档前可指定所属想定，持久化在 ScenarioData.objectives 内 */
    private String scenarioId;

    // 海上作战目标扩展（值域见 NavalDomainEnums.ObjectiveType）
    private String objectiveType;   // MISSION_TARGET/AREA_CONTROL/ESCORT/BLOCKADE
    private String targetAreaCode;  // 目标海域编码
    private double controlThreshold; // 区域控制判定阈值（0-1）
    private String escortTargetUnitId; // 护航目标
    private String blockadeRouteId; // 封锁航线
}

