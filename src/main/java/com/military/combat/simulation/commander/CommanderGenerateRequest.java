package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class CommanderGenerateRequest {
    private String scenarioId;
    private String goal;
    private String commanderSide = "RED";
    private String commanderRole = "ATTACK";
    private int runsPerStrategy = 5;
    private int roundsPerRun = 20;
    private String scorePerspective = "RED";
    /** 可选：策略评估与寻优相关的 seed（为空则不强制设置）。 */
    private Long seed;
}

