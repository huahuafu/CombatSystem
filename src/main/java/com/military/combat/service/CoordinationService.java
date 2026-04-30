package com.military.combat.service;

import com.military.combat.entity.CoordinationAction;
import com.military.combat.entity.CombatUnit;
import com.military.combat.repository.CoordinationActionRepository;
import com.military.combat.repository.CombatUnitRepository;
import com.military.combat.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CoordinationService {

    @Autowired
    private CoordinationActionRepository coordinationActionRepository;

    @Autowired
    private CombatUnitRepository unitRepository;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private ScenarioService scenarioService;

    // --- 协同作战管理 ---
    public CoordinationAction addCoordinationAction(CoordinationAction action) {
        if (action.getId() == null) {
            action.setId(java.util.UUID.randomUUID().toString());
        }
        if (action.getStatus() == null) {
            action.setStatus("PLANNED");
        }
        if ((action.getScenarioId() == null || action.getScenarioId().isEmpty())
                && scenarioService.getActiveScenarioId() != null
                && !scenarioService.getActiveScenarioId().isEmpty()) {
            action.setScenarioId(scenarioService.getActiveScenarioId());
        }
        return coordinationActionRepository.save(action);
    }
    
    public List<CoordinationAction> getAllCoordinationActions() {
        return coordinationActionRepository.findAll();
    }

    /** 推演引擎使用：按当前活跃想定严格过滤。 */
    public List<CoordinationAction> getCoordinationActionsForEngine() {
        String active = scenarioService.getActiveScenarioId();
        if (active == null || active.isEmpty()) {
            return new ArrayList<>();
        }
        List<CoordinationAction> all = coordinationActionRepository.findAll();
        List<CoordinationAction> out = new ArrayList<>();
        for (CoordinationAction action : all) {
            if (action != null && active.equals(action.getScenarioId())) {
                out.add(action);
            }
        }
        return out;
    }

    /** 编辑器列表：按想定严格过滤。 */
    public List<CoordinationAction> getCoordinationActionsForEditor(String scenarioId) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return coordinationActionRepository.findAll();
        }
        List<CoordinationAction> all = coordinationActionRepository.findAll();
        List<CoordinationAction> out = new ArrayList<>();
        for (CoordinationAction action : all) {
            if (action != null && scenarioId.equals(action.getScenarioId())) {
                out.add(action);
            }
        }
        return out;
    }
    
    public List<CoordinationAction> getCoordinationActionsBySide(String side) {
        return coordinationActionRepository.findBySide(side);
    }
    
    public List<CoordinationAction> getCoordinationActionsByStatus(String status) {
        return coordinationActionRepository.findByStatus(status);
    }
    
    public void deleteCoordinationAction(String id) {
        coordinationActionRepository.deleteById(id);
    }
    
    /**
     * 执行协同作战动作
     */
    public void executeCoordinationAction(CoordinationAction action, int currentRound) {
        if (action.getUnitIds() == null || action.getUnitIds().isEmpty()) {
            return;
        }
        
        List<CombatUnit> units = new ArrayList<>();
        for (String unitId : action.getUnitIds()) {
            CombatUnit unit = unitRepository.findById(unitId).orElse(null);
            if (unit != null && unit.getCombatPower() > 0) {
                units.add(unit);
            }
        }
        
        if (units.isEmpty()) {
            return;
        }
        
        adjustFormation(units, action);

        for (CombatUnit unit : units) {
            if (action.getSpeedMultiplier() > 0) {
                unit.setSpeed(unit.getSpeed() * action.getSpeedMultiplier());
            }
            combatUnitService.updateUnit(unit);
        }

        action.setStatus("EXECUTING");
        action.setCurrentRound(currentRound);
        coordinationActionRepository.save(action);
    }

    /**
     * 将参与单位向编队几何中心收拢，并按编队类型做轻微偏移（地理坐标优先）。
     */
    private void adjustFormation(List<CombatUnit> units, CoordinationAction action) {
        if (units.size() < 2) {
            return;
        }
        double maxStepMeters = action.getFormationDistance() > 0 ? action.getFormationDistance() : 12000.0;
        String ft = action.getFormationType() != null ? action.getFormationType() : "CIRCLE";

        boolean allGeo = true;
        for (CombatUnit u : units) {
            if (u.getLatitude() == null || u.getLongitude() == null) {
                allGeo = false;
                break;
            }
        }

        if (allGeo) {
            double sumLat = 0, sumLon = 0;
            for (CombatUnit u : units) {
                sumLat += u.getLatitude();
                sumLon += u.getLongitude();
            }
            double cLat = sumLat / units.size();
            double cLon = sumLon / units.size();

            for (int i = 0; i < units.size(); i++) {
                CombatUnit u = units.get(i);
                double[] target = formationTargetGeo(cLat, cLon, units, i, ft);
                double dist = GeoUtils.calculateDistance(u.getLatitude(), u.getLongitude(), target[0], target[1]);
                double step = Math.min(maxStepMeters, Math.max(2000.0, dist * 0.35));
                double[] np = GeoUtils.moveTowards(u.getLatitude(), u.getLongitude(), target[0], target[1], step);
                u.setLatitude(np[0]);
                u.setLongitude(np[1]);
            }
            return;
        }

        double sx = 0, sy = 0;
        for (CombatUnit u : units) {
            sx += u.getX();
            sy += u.getY();
        }
        double cx = sx / units.size();
        double cy = sy / units.size();
        double pctStep = 0.03;

        for (int i = 0; i < units.size(); i++) {
            CombatUnit u = units.get(i);
            double[] t = formationTargetPercent(cx, cy, units, i, ft);
            double dx = t[0] - u.getX();
            double dy = t[1] - u.getY();
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-9) {
                continue;
            }
            double move = Math.min(pctStep, len * 0.4);
            u.setX(u.getX() + dx / len * move);
            u.setY(u.getY() + dy / len * move);
        }
    }

    private double[] formationTargetGeo(double cLat, double cLon, List<CombatUnit> units, int index, String formationType) {
        int n = units.size();
        double spreadM = 0.00015 * (index - (n - 1) / 2.0);
        if ("LINE".equals(formationType)) {
            return new double[]{cLat + spreadM * 0.3, cLon + spreadM};
        }
        if ("COLUMN".equals(formationType)) {
            return new double[]{cLat + spreadM * 1.2, cLon};
        }
        if ("WEDGE".equals(formationType)) {
            double f = index / (double) Math.max(1, n - 1);
            return new double[]{cLat + spreadM * 0.5, cLon + (f - 0.5) * spreadM};
        }
        return new double[]{cLat, cLon};
    }

    private double[] formationTargetPercent(double cx, double cy, List<CombatUnit> units, int index, String formationType) {
        int n = units.size();
        double spread = 0.012 * (index - (n - 1) / 2.0);
        if ("LINE".equals(formationType)) {
            return new double[]{cx + spread * 0.3, cy + spread};
        }
        if ("COLUMN".equals(formationType)) {
            return new double[]{cx + spread * 1.0, cy};
        }
        if ("WEDGE".equals(formationType)) {
            double f = index / (double) Math.max(1, n - 1);
            return new double[]{cx + spread * 0.4, cy + (f - 0.5) * spread};
        }
        return new double[]{cx, cy};
    }
}
