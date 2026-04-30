package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class CommanderExecuteRequest {
    private String scenarioId;
    private String goal;
    private CommanderStrategyLabel label;
    private String strategyId;
    private CommanderStrategyCard strategy;

    private int rounds = 8;
    private String scorePerspective = "RED";

    /** 可选：推演 seed；为空时服务端自动生成并回传，便于复现。 */
    private Long seed;

    /** 可选：用于审计/回放关联 */
    private String requestId;
}

