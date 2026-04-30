package com.military.combat.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 作战活动实体 —— <strong>主线第三层：对本战役下部队的命令与行为编排</strong>（步骤化移动/攻击/防守等）。
 * 应绑定 {@link #scenarioId} 与 {@link #campaignId}，与想定兵力部署及战役文档一致。
 * <p>根类型不设 {@code @AllArgsConstructor}，避免 Jackson 选用全参构造器导致 REST 反序列化失败；步骤行仍保留全参构造供模板代码使用。</p>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CombatActivity {
    private String id;                    // 活动ID
    private String name;                  // 活动名称
    private String type;                  // 活动类型 (ATTACK-进攻, DEFEND-防守, RECON-侦察, SUPPORT-支援, SIEGE-围困, INSERTION-穿插, FIRE_COVER-火力覆盖, RETREAT-撤退, RESUPPLY-补给)
    private String side;                  // 执行方 (RED/BLUE)
    private String description;           // 活动描述
    private String scenarioId;            // 关联的想定ID（推演/列表按当前工作想定过滤）
    private String campaignId;            // 关联的战役ID
    private String parentActivityId;      // 父活动ID（用于活动嵌套）
    private List<String> childActivityIds; // 子活动ID列表
    
    // 参与单位
    private List<String> unitIds;         // 参与活动的单位ID列表
    private Map<String, String> unitRoles; // 单位角色映射（单位ID -> 角色）
    
    // 任务目标
    private String objectiveId;           // 关联的作战目标ID
    private String mission;               // 任务描述
    private String victoryCondition;      // 胜利条件
    private String failureCondition;      // 失败条件
    
    // 执行步骤
    private List<ActivityStep> steps;     // 执行步骤列表
    
    // 时间节点
    private int startRound;               // 开始回合
    private int endRound;                 // 结束回合（-1表示持续）
    private int duration;                 // 持续时间（回合数）
    private Date createdAt;               // 创建时间
    private Date updatedAt;               // 更新时间
    private Date startTime;               // 开始执行时间
    private Date endTime;                 // 结束执行时间
    private long executionDuration;       // 执行持续时间（毫秒）
    
    // 状态
    private String status;                // 状态 (PLANNED-计划中, READY-准备就绪, EXECUTING-执行中, PAUSED-暂停, COMPLETED-已完成, FAILED-失败, CANCELLED-已取消)
    private int currentStep;              // 当前执行步骤索引
    private int currentStepRound;         // 当前步骤执行的回合数
    
    // 执行条件
    private String condition;             // 执行条件（JSON格式，可扩展）
    private Map<String, Object> parameters; // 活动参数
    
    // 资源管理
    private Map<String, Integer> resourceRequirements; // 资源需求
    private Map<String, Integer> resourceConsumption; // 资源消耗
    
    // 地理信息
    private Double targetLatitude;        // 目标纬度
    private Double targetLongitude;       // 目标经度
    private Double targetRadius;          // 目标范围半径（米）
    private String targetArea;            // 目标区域描述
    
    // 结果和评估
    private String result;                // 活动结果
    private int successRate;              // 成功率（0-100）
    private List<String> logs;            // 活动日志
    
    // 战役关联（历史预设战役与自定义战役共用；阶段/目标可填 ID 或自由文本标签）
    private String campaignPhaseId;       // 兼容旧数据：阶段索引或内部键
    private String campaignPhaseLabel;    // 战役阶段说明（自由填写，可与战役文档中阶段名称一致或自定义）
    private String campaignObjectiveId;   // 战役目标 ID（若文档中已分配）
    private String campaignObjectiveLabel; // 战役目标名称或备注（优先于 ID 做名称匹配）
    private List<String> triggerEventIds; // 可能触发的关键事件ID列表
    private boolean phaseRestricted;      // 为 true 时仅在与当前推演阶段匹配时推进（可选，默认不限制）
    
    // 元数据
    private String createdBy;             // 创建者
    private String lastModifiedBy;        // 最后修改者
    private Map<String, Object> metadata; // 其他元数据
    
    /**
     * 活动步骤内部类（仅无参构造，供 Jackson 与业务 setter 填充；不设全参构造，避免反序列化走 Lombok 全参构造失败）
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ActivityStep {
        private int stepNumber;           // 步骤序号
        private String name;               // 步骤名称
        private String description;        // 步骤描述
        private String action;             // 动作类型 (MOVE-移动, ATTACK-攻击, WAIT-等待, COORDINATE-协同, DEFEND-防守, RECON-侦察, SUPPORT-支援, FIRE-开火, RESUPPLY-补给, COMMUNICATE-通信)
        private String target;             // 目标（单位ID或坐标）
        private String targetType;         // 目标类型 (UNIT-单位, LOCATION-位置, AREA-区域, OBJECTIVE-目标)
        private int roundDuration;         // 步骤持续回合数
        private int currentRound;          // 当前执行回合数
        private boolean completed;         // 是否完成
        private String status;             // 步骤状态 (PENDING-待执行, EXECUTING-执行中, COMPLETED-已完成, FAILED-失败)
        private Map<String, Object> parameters; // 步骤参数
        private List<String> subSteps;     // 子步骤ID列表
        private String condition;          // 执行条件
        private String successCondition;   // 成功条件
        private String failureCondition;   // 失败条件
        private Date startTime;            // 开始时间
        private Date endTime;              // 结束时间
        private String result;             // 步骤结果
        private List<String> logs;         // 步骤日志
    }
}

