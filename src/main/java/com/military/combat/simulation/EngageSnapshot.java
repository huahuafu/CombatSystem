package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Engage（交战）环节态势快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngageSnapshot {
    private String scenarioId;
    private String scenarioName;
    private int round;
    private long generatedAt;

    private int plannedFireSolutionCount;    // 来自 Target 的火力解
    private int executedStrikeCount;         // 实际执行交战数
    private int successfulHitCount;          // 命中/毁伤成功数
    private int collateralRiskCount;         // 潜在附带损伤风险数
    private double fireExecutionRate;        // 火力兑现率
    private double hitEffectivenessRate;     // 命中效率
    private double engageLatencySeconds;     // 交战时延
    private double collateralControlRate;    // 附带损伤控制率

    private String readinessLevel; // HIGH/MEDIUM/LOW
    private List<String> strengths = new ArrayList<>();
    private List<String> weaknesses = new ArrayList<>();
}
