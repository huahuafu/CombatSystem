package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 架次任务（Sortie）：母平台/航空兵执行侦察、干扰、打击等任务。
 */
@Data
@Document(collection = "sortie_missions")
public class SortieMission {
    @Id
    private String id;
    private String scenarioId;
    private String missionName;
    private String missionType;      // ISR/EW/STRIKE/CAP/ESCORT/ANTI_SUBMARINE/SEA_DENIAL
    private String parentPlatformId; // 航母/机场/陆航基地
    private String unitId;           // 执行单元
    private String objective;
    private String status;           // PLANNED/AIRBORNE/COMPLETED/ABORTED
    private int launchRound;
    private int recoverRound;
    private double fuelCost;         // 油料消耗百分比
    private int ammoCost;            // 弹药消耗
    private double riskLevel;        // 风险等级(0-1)
    private long createdAt;
}
