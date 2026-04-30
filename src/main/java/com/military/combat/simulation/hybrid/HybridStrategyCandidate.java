package com.military.combat.simulation.hybrid;

import lombok.Data;

/**
 * AI 推荐的候选策略（Top-N 单条）。
 */
@Data
public class HybridStrategyCandidate {
    private String strategyId;
    private String strategyName;
    private String hypothesis;
    private String riskSummary;
    private String resourcePrediction;
    private double predictedWinRate;
    private double predictedExpectedLoss;
    private double predictedMissionSuccessRate;
    private double confidence;
    /**
     * 策略计划原始 JSON（用于追溯与前端回显）。
     */
    private String planJson;
}
