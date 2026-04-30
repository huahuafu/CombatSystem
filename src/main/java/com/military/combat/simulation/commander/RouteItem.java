package com.military.combat.simulation.commander;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RouteItem {
    private String routeId;
    private String forUnitType;
    private String phase;
    private String trigger;
    private List<RouteWaypoint> waypoints = new ArrayList<>();
}
