package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 编组指挥命令：母平台向子单元下发的任务指令。
 */
@Data
@Document(collection = "group_command_orders")
public class GroupCommandOrder {
    @Id
    private String id;
    private String scenarioId;
    private String groupKey;          // 编组标识（如 CARRIER_STRIKE_GROUP）
    private String commanderUnitId;   // 母平台ID
    private String orderType;         // ISR/EW/STRIKE/AIR_DEFENSE/RESUPPLY
    private String objective;
    private String status;            // PLANNED/ISSUED/EXECUTING/COMPLETED/FAILED
    private int issueRound;
    private int expectedFinishRound;
    private int fuelBudget;           // 编组油料预算（抽象）
    private int ammoBudget;           // 编组弹药预算（抽象）
    private int usedFuel;
    private int usedAmmo;
    private String notes;
    private long createdAt;
}
