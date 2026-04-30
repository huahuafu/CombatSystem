package com.military.combat.simulation.commander;

import lombok.Data;

@Data
public class DeploymentItem {
    private String unitType;
    private int unitCount;
    private String side;
    private double latitude;
    private double longitude;
    private Integer facing;
    private String task;
}
