package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class CommanderExecuteResponse {
    private String requestId;
    private String scenarioId;
    private CommanderStrategyLabel label;
    private String strategyId;
    private long seed;
    private CommanderBattleReport report;
}

