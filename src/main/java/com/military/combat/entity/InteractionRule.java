package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 交互规则实体 —— <strong>主线第四层：交战/协同规则</strong>（通常挂当前想定；空 scenarioId 表示全局规则）。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class InteractionRule {
    private String id;                      // 规则ID
    private String scenarioId;              // 关联想定（空=全局规则，对所有想定生效）
    /** 冗余：所属战役 ID，与想定绑定的战役一致，便于按战役筛选 */
    private String campaignId;
    private String name;                    // 规则名称
    private String type;                    // 规则类型 (COORDINATE-协同, ATTACK-攻击, DEFEND-防守, SUPPORT-支援)
    private String description;             // 规则描述
    
    // 触发条件
    private String triggerCondition;       // 触发条件（JSON格式）
    private String triggerType;             // 触发类型 (DISTANCE-距离, TIME-时间, EVENT-事件, CONDITION-条件)
    
    // 参与单位
    private List<String> sourceUnitIds;    // 发起方单位ID列表
    private List<String> targetUnitIds;    // 目标方单位ID列表
    private String sourceSide;             // 发起方阵营
    private String targetSide;             // 目标方阵营
    
    // 交互效果
    private Map<String, Object> effects;   // 效果参数（JSON格式）
    private double effectValue;            // 效果数值
    private String effectType;             // 效果类型 (DAMAGE_BOOST-伤害加成, SPEED_BOOST-速度加成, DEFENSE_BOOST-防御加成)
    
    // 执行条件
    private double minDistance;           // 最小距离（米）
    private double maxDistance;           // 最大距离（米）
    private int startRound;                // 开始回合
    private int endRound;                  // 结束回合（-1表示持续）
    
    // 状态
    private boolean enabled;               // 是否启用
    private int priority;                  // 优先级（数字越大优先级越高）
}
