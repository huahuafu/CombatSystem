package com.military.combat.simulation.hybrid;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class HybridStrategyOptimizeResponse {
    private String scenarioId;
    private int runsPerStrategy;
    private int roundsPerRun;
    private String winnerStrategyId;
    private String winnerSummary;
    private List<StrategyEvaluation> evaluations = new ArrayList<>();
    private List<String> topFactors = new ArrayList<>();

    @Data
    public static class StrategyEvaluation {
        private String strategyId;
        private String strategyName;
        private double projectedScore;
        private double batchMeanScore;
        private double batchBestScore;
        private double batchWinRate;
        private double finalScore;
        private List<String> explanation = new ArrayList<>();
        private Map<String, Double> strategyDelta = new LinkedHashMap<>();
    }
}
