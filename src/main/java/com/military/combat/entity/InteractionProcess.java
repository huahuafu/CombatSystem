package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

/**
 * 交互过程实体 —— 推演中按规则执行的交互过程实例；与 {@link #scenarioId} 想定及关联规则联动。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Document
public class InteractionProcess {
    private String id;                    // 过程ID
    @Indexed
    private String scenarioId;            // 关联想定
    private String ruleId;                // 关联的规则ID
    private String ruleName;              // 规则名称
    private String processName;           // 过程名称
    private String description;           // 过程描述
    
    // 执行信息
    @Indexed
    private int round;                    // 执行回合
    private String sourceSide;            // 发起方
    private String targetSide;            // 目标方
    private List<String> sourceUnits;     // 发起单位列表
    private List<String> targetUnits;     // 目标单位列表
    
    // 过程状态
    @Indexed
    private String status;                // 状态 (PENDING-待执行, EXECUTING-执行中, COMPLETED-已完成, FAILED-失败, CANCELLED-已取消)
    private int currentStep;              // 当前步骤
    private int totalSteps;               // 总步骤数
    private List<ProcessStep> steps;      // 执行步骤列表
    
    // 执行结果
    private String result;                 // 执行结果 (SUCCESS-成功, FAILED-失败, PARTIAL-部分成功)
    private String effect;                // 产生的效果描述
    private double effectValue;           // 效果数值
    private Map<String, Object> effects;   // 详细效果参数
    
    // 时间信息
    private String executeTime;           // 执行时间
    private String startTime;             // 开始时间
    private String endTime;               // 结束时间
    private int duration;                 // 持续时间（回合）
    
    // 相关联的活动和目标
    private String activityId;            // 关联的活动ID
    private String objectiveId;           // 关联的目标ID
    
    // 地理位置信息
    private Double latitude;              // 执行位置纬度
    private Double longitude;             // 执行位置经度
    
    /**
     * 过程步骤实体
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessStep {
        private int stepNumber;           // 步骤编号
        private String name;              // 步骤名称
        private String description;       // 步骤描述
        private String action;            // 步骤动作 (MOVE-移动, ATTACK-攻击, DEFEND-防守, SUPPORT-支援, WAIT-等待)
        private String target;            // 步骤目标
        private int roundDuration;        // 持续回合数
        private String status;            // 步骤状态 (PENDING-待执行, EXECUTING-执行中, COMPLETED-已完成, FAILED-失败)
        private String result;            // 步骤结果
        private double progress;          // 完成进度 (0-100)
    }
}

