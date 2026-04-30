package com.military.combat.service;

import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.Terrain;
import com.military.combat.repository.CombatObjectiveRepository;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 想定作战目标与单位的绑定解析，以及单位到目标点的几何关系（地理/百分比坐标）。
 */
@Service
public class CombatObjectiveDirectiveService {

    /** 视为「已抵达」占领/摧毁区域的距离（米） */
    public static final double ARRIVAL_RADIUS_METERS = 8000.0;
    /** 百分比坐标下视为抵达 */
    public static final double ARRIVAL_RADIUS_PCT = 0.028;
    /** 防守任务允许离开目标点的最大距离，超出则先回撤 */
    public static final double DEFEND_MAX_DRIFT_METERS = 15000.0;
    public static final double DEFEND_MAX_DRIFT_PCT = 0.045;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatObjectiveRepository objectiveRepository;

    /**
     * 解析单位当前应服从的作战目标：优先 {@link CombatUnit#getObjectiveId()}；
     * 若无则选用当前想定下同阵营、未完成、优先级最高的目标。
     */
    public CombatObjective resolveObjectiveForUnit(CombatUnit unit) {
        if (unit == null) {
            return null;
        }
        String sid = scenarioService.getActiveScenarioId();
        List<CombatObjective> scenarioObjectives = sid != null && !sid.isEmpty()
                ? scenarioService.getObjectivesForScenario(sid)
                : List.of();

        String oid = unit.getObjectiveId();
        if (oid != null && !oid.isEmpty()) {
            CombatObjective o = findByIdInList(scenarioObjectives, oid);
            if (o == null) {
                o = objectiveRepository.findById(oid).orElse(null);
            }
            if (o != null && objectiveAppliesToUnit(o, unit) && !o.isCompleted()) {
                return o;
            }
        }

        return scenarioObjectives.stream()
                .filter(o -> objectiveAppliesToUnit(o, unit))
                .filter(o -> !o.isCompleted())
                .max(Comparator.comparingInt(CombatObjective::getPriority))
                .orElse(null);
    }

    private static CombatObjective findByIdInList(List<CombatObjective> list, String id) {
        if (list == null || id == null) {
            return null;
        }
        for (CombatObjective o : list) {
            if (id.equals(o.getId())) {
                return o;
            }
        }
        return null;
    }

    private boolean objectiveAppliesToUnit(CombatObjective o, CombatUnit unit) {
        if (o == null || unit == null) {
            return false;
        }
        if (o.getSide() != null && !o.getSide().isEmpty()
                && unit.getSide() != null && !o.getSide().equals(unit.getSide())) {
            return false;
        }
        return hasObjectivePosition(o);
    }

    public boolean hasObjectivePosition(CombatObjective o) {
        if (o == null) {
            return false;
        }
        if (o.getLatitude() != null && o.getLongitude() != null) {
            return true;
        }
        return o.getX() != 0 || o.getY() != 0;
    }

    public double distanceUnitToObjective(CombatUnit u, CombatObjective o) {
        if (u == null || o == null) {
            return Double.POSITIVE_INFINITY;
        }
        if (u.getLatitude() != null && u.getLongitude() != null
                && o.getLatitude() != null && o.getLongitude() != null) {
            return GeoUtils.calculateDistance(
                    u.getLatitude(), u.getLongitude(),
                    o.getLatitude(), o.getLongitude());
        }
        return Math.sqrt(Math.pow(u.getX() - o.getX(), 2) + Math.pow(u.getY() - o.getY(), 2));
    }

    public boolean isWithinArrivalRadius(CombatUnit u, CombatObjective o) {
        double d = distanceUnitToObjective(u, o);
        if (u.getLatitude() != null && u.getLongitude() != null
                && o.getLatitude() != null && o.getLongitude() != null) {
            return d <= ARRIVAL_RADIUS_METERS;
        }
        return d <= ARRIVAL_RADIUS_PCT;
    }

    public boolean isWithinDefendHold(CombatUnit u, CombatObjective o) {
        double d = distanceUnitToObjective(u, o);
        if (u.getLatitude() != null && u.getLongitude() != null
                && o.getLatitude() != null && o.getLongitude() != null) {
            return d <= DEFEND_MAX_DRIFT_METERS;
        }
        return d <= DEFEND_MAX_DRIFT_PCT;
    }

    /**
     * 向作战目标点机动一步（与 {@link CombatEngineService#moveTowards} 步长风格一致）。
     */
    public void moveUnitOneStepTowardsObjective(CombatUnit mover, CombatObjective objective,
                                                TerrainService terrainService, CombatUnitService unitService) {
        if (mover == null || objective == null || !hasObjectivePosition(objective)) {
            return;
        }
        double baseDistance = mover.getSpeed() * 10000;
        double speedFactor = 1.0;
        Terrain t = terrainService.getUnitTerrain(mover);
        if (t != null) {
            speedFactor = terrainService.getTerrainSpeedModifier(t);
        }
        double step = Math.max(baseDistance * speedFactor, 50000 * speedFactor);

        if (mover.getLatitude() != null && mover.getLongitude() != null
                && objective.getLatitude() != null && objective.getLongitude() != null) {
            double[] np = GeoUtils.moveTowards(
                    mover.getLatitude(), mover.getLongitude(),
                    objective.getLatitude(), objective.getLongitude(),
                    step);
            mover.setLatitude(np[0]);
            mover.setLongitude(np[1]);
        } else {
            double dx = objective.getX() - mover.getX();
            double dy = objective.getY() - mover.getY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-9) {
                return;
            }
            mover.setX(mover.getX() + dx / len * Math.min(step * 1e-6, len));
            mover.setY(mover.getY() + dy / len * Math.min(step * 1e-6, len));
        }
        unitService.updateUnit(mover);
    }
}
