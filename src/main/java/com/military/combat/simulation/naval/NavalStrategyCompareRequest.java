package com.military.combat.simulation.naval;

import lombok.Data;

@Data
public class NavalStrategyCompareRequest {
    private String scenarioId;
    private String goal;
    private String scorePerspective = "RED";
    private int topN = 3;
    private int runsPerStrategy = 5;
    private int roundsPerRun = 20;
}
