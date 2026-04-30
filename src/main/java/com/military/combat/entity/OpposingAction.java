package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 敌方/对手对抗动作模型。
 */
@Data
@Document(collection = "opposing_actions")
public class OpposingAction {
    @Id
    private String id;
    private String scenarioId;
    private String side;            // RED/BLUE
    private String actionType;      // COUNTER_RECON/EW_SUPPRESSION/DECOY/SAM_INTERCEPT/SATURATION_STRIKE/ROUTE_BREACH
    private String targetDomain;    // LAND/SEA/AIR/EM
    private String status;          // PLANNED/ACTIVE/EXPIRED
    private int startRound;
    private int endRound;
    private double intensity;       // 0~1
    private String strategyTemplate; // 模板标识（如 NAVAL_DECOY_TRAP）
    private int priority;           // 执行优先级（越大越先）
    private double executionWeight; // 执行权重（0~2，默认1）
    private int lastAppliedRound;   // 最近一次应用到引擎的回合
    private String objective;
    private long createdAt;
}
