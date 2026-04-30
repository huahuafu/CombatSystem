package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class TempoNode {
    private int tPlusMin;
    private String milestone;
    private String pace;
    private String expectedOutcome;
}
