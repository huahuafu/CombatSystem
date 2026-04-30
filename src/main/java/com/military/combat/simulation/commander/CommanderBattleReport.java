package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommanderBattleReport {
    private String winner;
    private String winReason;

    private double finalScore;
    private double batchWinRate;
    private double missionSuccessRate;
    private double expectedLoss;

    private List<String> explanations = new ArrayList<>();
    private List<KillChainStageSnapshotV3> stageSnapshots = new ArrayList<>();
    private List<CommanderPhaseReport> phaseReports = new ArrayList<>();
}

