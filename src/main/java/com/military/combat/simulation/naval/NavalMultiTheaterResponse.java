package com.military.combat.simulation.naval;

import com.military.combat.simulation.batch.BatchRunOutcome;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NavalMultiTheaterResponse {
    private String scenarioId;
    private String rolloutPhase;
    private String validationSummary;
    private String canarySummary;
    private String commanderBrief;
    private double aggregateScore;
    private double missionSuccessRate;
    private double fleetSurvivalRate;
    private double routeSecurityRate;
    private List<TheaterOutcome> theaters = new ArrayList<>();
    private List<ResourceReallocationSuggestion> reallocationSuggestions = new ArrayList<>();
    private List<String> verificationChecklist = new ArrayList<>();

    @Data
    public static class TheaterOutcome {
        private String theaterId;
        private String theaterName;
        private String areaCode;
        private String taskType;
        private int priority;
        private double resourceWeight;
        private double score;
        private double winRate;
        private double missionSuccessRate;
        private double fleetSurvivalRate;
        private double routeSecurityRate;
        private int bestRunIndex;
        private List<BatchRunOutcome> topRuns = new ArrayList<>();
    }

    @Data
    public static class ResourceReallocationSuggestion {
        private String fromTheaterId;
        private String fromTheaterName;
        private String toTheaterId;
        private String toTheaterName;
        private double transferWeight;
        private String reason;
        private String expectedImpact;
    }
}
