package com.military.combat.simulation.debrief;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DebriefRequest {
    /** RED 或 BLUE */
    private String commanderSide = "RED";
    private String winner;
    private String winReason;
    private List<DebriefRoundSummary> rounds = new ArrayList<>();
}
