package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class SequenceStep {
    private int step;
    private String action;
    private String actor;
    private String target;
    private String priority;
    private String dependency;
}
