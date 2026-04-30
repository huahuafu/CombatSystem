package com.military.combat.simulation.naval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NavalStrategyCompareResponse {
    private String scenarioId;
    private String winnerStrategyId;
    private String winnerSummary;
    private SituationLayer situation = new SituationLayer();
    private List<CompareItem> strategyBoard = new ArrayList<>();

    @Data
    public static class CompareItem {
        private String strategyId;
        private String strategyName;
        private double projectedWinRate;
        private double expectedLoss;
        private double missionSuccessRate;
        private double confidence;
        private double finalScore;
        private double batchWinRate;
        private double winRateCiLow;
        private double winRateCiHigh;
        private double stabilityScore;
        private List<String> topFactors = new ArrayList<>();
        private List<String> explanation = new ArrayList<>();
    }

    @Data
    public static class SituationLayer {
        private double seaControlIndex;
        private double threatHeat;
        private double keyNodeContestRate;
        private double ammoFuelExhaustionRisk;
        private double detectionExposureRisk;
    }
}
