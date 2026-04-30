package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 协同作战动作实体
 * 定义多个单位之间的协同作战行为
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoordinationAction {
    private String id;                      // 动作ID
    private String scenarioId;              // 关联想定ID（用于严格作用域）
    private String name;                   // 动作名称
    private String type;                    // 动作类型 (FLANK-侧翼, ENCIRCLE-包围, COVER-掩护, FIRE_SUPPORT-火力支援)
    private String description;            // 动作描述
    
    // 参与单位
    private List<String> unitIds;          // 参与单位ID列表
    private String side;                    // 所属阵营
    
    // 目标
    private String targetId;                // 目标单位ID或目标位置
    private String targetType;              // 目标类型 (UNIT-单位, POSITION-位置, OBJECTIVE-目标)
    
    // 执行参数
    private double formationDistance;      // 编队距离（米）
    private String formationType;          // 编队类型 (LINE-线形, COLUMN-纵队, WEDGE-楔形, CIRCLE-圆形)
    private int duration;                  // 持续时间（回合数）
    
    // 协同效果
    private double damageMultiplier;       // 伤害倍数
    private double speedMultiplier;         // 速度倍数
    private double defenseMultiplier;      // 防御倍数
    
    // 状态
    private String status;                 // 状态 (PLANNED-计划中, EXECUTING-执行中, COMPLETED-已完成)
    private int currentRound;               // 当前回合
}

