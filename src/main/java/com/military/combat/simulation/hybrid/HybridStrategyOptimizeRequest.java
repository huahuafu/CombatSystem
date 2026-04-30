package com.military.combat.simulation.hybrid;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class HybridStrategyOptimizeRequest {
    private String goal;
    private String scenarioId;
    private int topN = 3;
    private int runsPerStrategy = 5;
    private int roundsPerRun = 20;
    private String scorePerspective = "RED";
    private List<HybridStrategyCandidate> strategies = new ArrayList<>();
}
