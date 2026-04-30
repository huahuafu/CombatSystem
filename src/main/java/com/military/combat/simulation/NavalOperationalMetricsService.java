package com.military.combat.simulation;

import com.military.combat.entity.CombatUnit;
import com.military.combat.service.CombatUnitService;
import com.military.combat.service.ScenarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NavalOperationalMetricsService {

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private ScenarioService scenarioService;

    public NavalOperationalMetricsSnapshot current() {
        NavalOperationalMetricsSnapshot out = new NavalOperationalMetricsSnapshot();
        out.setScenarioId(scenarioService.getActiveScenarioId());
        out.setRound(scenarioService.getCurrentRound());

        List<CombatUnit> all = combatUnitService.getUnitsForBattleEngine();
        List<CombatUnit> naval = all.stream()
                .filter(u -> u != null && "SEA".equalsIgnoreCase(u.getDomain()))
                .toList();
        List<CombatUnit> alive = naval.stream().filter(u -> u.getCombatPower() > 0).toList();
        List<CombatUnit> redAlive = alive.stream().filter(u -> "RED".equalsIgnoreCase(u.getSide())).toList();
        List<CombatUnit> blueAlive = alive.stream().filter(u -> "BLUE".equalsIgnoreCase(u.getSide())).toList();

        out.setNavalUnitCount(naval.size());
        out.setRedNavalAlive(redAlive.size());
        out.setBlueNavalAlive(blueAlive.size());

        double control = alive.stream().mapToDouble(u -> clamp01(u.getSeaControlContribution())).average().orElse(0.0);
        out.setSeaControlIndex(round3(control));

        double airDefense = alive.stream()
                .filter(u -> "AIR_DEFENSE".equalsIgnoreCase(u.getNavalTaskType()) || missionOf(u).contains("AIR_DEFENSE"))
                .mapToDouble(u -> clamp01(u.getAntiAirCapability()))
                .average().orElse(0.0);
        out.setFleetAirDefenseCoverageRate(round3(airDefense));

        double antiSub = alive.stream()
                .filter(u -> "ANTI_SUBMARINE".equalsIgnoreCase(u.getNavalTaskType()) || missionOf(u).contains("ANTI_SUBMARINE"))
                .mapToDouble(u -> clamp01(u.getAntiSubCapability()))
                .average().orElse(0.0);
        out.setAntiSubPatrolCoverageRate(round3(antiSub));

        double denial = alive.stream()
                .filter(u -> missionOf(u).contains("BLOCKADE") || missionOf(u).contains("SEA_DENIAL"))
                .mapToDouble(u -> clamp01(0.5 * u.getSeaControlContribution() + 0.5 * u.getReadinessLevel()))
                .average().orElse(0.0);
        out.setAreaDenialPressure(round3(denial));

        double route = alive.stream()
                .filter(u -> missionOf(u).contains("ESCORT") || missionOf(u).contains("ROUTE_MANEUVER"))
                .mapToDouble(u -> clamp01(0.6 * u.getReadinessLevel() + 0.4 * (u.getFuelLevel() / 100.0)))
                .average().orElse(0.0);
        out.setRouteSecurityRate(round3(route));

        out.setFleetSurvivalRate(round3(rate(alive.size(), naval.size())));
        out.setAmmoFuelExhaustionRisk(round3(computeExhaustionRisk(alive)));
        return out;
    }

    private double computeExhaustionRisk(List<CombatUnit> alive) {
        if (alive.isEmpty()) {
            return 1.0;
        }
        long critical = alive.stream()
                .filter(u -> u.getFuelLevel() < 12 || u.getAmmoLevel() < 12 || "CRITICAL".equalsIgnoreCase(u.getSupplyStatus()))
                .count();
        long low = alive.stream()
                .filter(u -> u.getFuelLevel() < 25 || u.getAmmoLevel() < 25 || "LOW".equalsIgnoreCase(u.getSupplyStatus()))
                .count();
        double v = (critical * 1.0 + low * 0.5) / alive.size();
        return clamp01(v);
    }

    private static String missionOf(CombatUnit u) {
        return u == null || u.getMission() == null ? "" : u.getMission().toUpperCase();
    }

    private static double rate(int a, int b) {
        if (b <= 0) {
            return 0;
        }
        return Math.max(0.0, Math.min(1.0, (double) a / b));
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
