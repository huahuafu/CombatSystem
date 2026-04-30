package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CommanderGenerateResponse {
    private String requestId;
    private String scenarioId;
    private String goal;
    private Long seed;
    private List<CommanderStrategyCard> strategies = new ArrayList<>();
    private List<String> notes = new ArrayList<>();
}

