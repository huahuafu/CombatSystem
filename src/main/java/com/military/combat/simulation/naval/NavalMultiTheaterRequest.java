package com.military.combat.simulation.naval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NavalMultiTheaterRequest {
    private String scenarioId;
    private String scorePerspective = "RED";
    private int runsPerTheater = 3;
    private int roundsPerRun = 20;
    private boolean replaceAiArtifacts = false;
    private List<TheaterTask> theaters = new ArrayList<>();
    private RolloutProfile rollout = new RolloutProfile();

    @Data
    public static class TheaterTask {
        private String theaterId;
        private String theaterName;
        private String areaCode;
        private String taskType;
        private int priority = 5;
        private double resourceWeight = 1.0;
    }

    @Data
    public static class RolloutProfile {
        private String phase = "PILOT";
        private int pilotTrafficPercent = 20;
        private String validationLevel = "STANDARD";
        private boolean enableCanary = true;
    }
}
