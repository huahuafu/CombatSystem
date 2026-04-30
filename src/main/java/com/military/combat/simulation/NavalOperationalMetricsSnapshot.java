package com.military.combat.simulation;

import lombok.Data;

@Data
public class NavalOperationalMetricsSnapshot {
    private String scenarioId;
    private int round;
    private int navalUnitCount;
    private int redNavalAlive;
    private int blueNavalAlive;
    private double seaControlIndex;
    private double fleetAirDefenseCoverageRate;
    private double antiSubPatrolCoverageRate;
    private double areaDenialPressure;
    private double routeSecurityRate;
    private double fleetSurvivalRate;
    private double ammoFuelExhaustionRisk;
}
