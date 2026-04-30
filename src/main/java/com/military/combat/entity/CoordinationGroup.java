package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 协同作战组实体
 * 定义多个单位之间的协同关系
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoordinationGroup {
    private String id;                    // 组ID
    private String name;                  // 组名称
    private String type;                  // 协同类型 (FORMATION-编队, SUPPORT-支援, COVER-掩护)
    private String side;                  // 所属阵营
    
    // 参与单位
    private List<String> unitIds;         // 参与协同的单位ID列表
    private String leaderUnitId;          // 指挥单位ID
    
    // 协同规则
    private String coordinationRule;     // 协同规则（JSON格式）
    private double coordinationBonus;    // 协同加成
    private int maxDistance;              // 最大协同距离（米）
    
    // 状态
    private String status;                // 状态 (ACTIVE-活跃, DISBANDED-解散)
    private int formationType;            // 队形类型 (1-线形, 2-圆形, 3-楔形)
}

