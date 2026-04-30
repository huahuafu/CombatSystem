package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 交互事件实体
 * 记录红蓝双方在作战中的交互过程
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InteractionEvent {
    private String id;                      // 事件ID
    private String ruleId;                  // 触发的规则ID
    private String ruleName;                // 规则名称
    private String type;                    // 事件类型 (COORDINATE-协同, ATTACK-攻击, DEFEND-防守)
    
    // 参与单位
    private String sourceUnitId;            // 发起单位ID
    private String sourceUnitName;          // 发起单位名称
    private String targetUnitId;            // 目标单位ID（可选）
    private String targetUnitName;          // 目标单位名称（可选）
    
    // 交互结果
    private String result;                  // 交互结果 (SUCCESS-成功, FAILED-失败, PARTIAL-部分成功)
    private double effectValue;             // 效果数值
    private String description;             // 事件描述
    
    // 时间信息
    private int round;                      // 发生回合
    private long timestamp;                 // 时间戳
    
    // 位置信息
    private Double latitude;                // 发生位置纬度
    private Double longitude;               // 发生位置经度
}

