package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.SortieMission;
import com.military.combat.repository.SortieMissionRepository;
import com.military.combat.util.NavalDomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 架次任务服务：任务创建、放飞、回收与资源消耗。
 */
@Service
public class SortieMissionService {

    @Autowired
    private SortieMissionRepository sortieMissionRepository;

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    public List<SortieMission> listForActiveScenario() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) return new ArrayList<>();
        return sortieMissionRepository.findByScenarioId(sid);
    }

    public SortieMission createMission(SortieMission mission) {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) {
            throw new IllegalStateException("未激活想定，无法创建架次任务");
        }
        if (mission.getUnitId() == null || mission.getUnitId().isEmpty()) {
            throw new IllegalArgumentException("unitId 不能为空");
        }
        NavalDomainValidator.normalizeAndValidateSortie(mission);
        if (mission.getId() == null || mission.getId().isEmpty()) mission.setId(UUID.randomUUID().toString());
        mission.setScenarioId(sid);
        if (mission.getStatus() == null) mission.setStatus("PLANNED");
        if (mission.getLaunchRound() <= 0) mission.setLaunchRound(scenarioService.getCurrentRound());
        if (mission.getRecoverRound() <= mission.getLaunchRound()) mission.setRecoverRound(mission.getLaunchRound() + 2);
        if (mission.getFuelCost() <= 0) mission.setFuelCost(15);
        if (mission.getAmmoCost() <= 0) mission.setAmmoCost(8);
        mission.setCreatedAt(System.currentTimeMillis());
        return sortieMissionRepository.save(mission);
    }

    public List<SortieMission> runForCurrentRound() {
        int round = scenarioService.getCurrentRound();
        List<SortieMission> all = listForActiveScenario();
        List<SortieMission> changed = new ArrayList<>();
        for (SortieMission m : all) {
            if ("PLANNED".equals(m.getStatus()) && round >= m.getLaunchRound()) {
                launchMission(m);
                changed.add(sortieMissionRepository.save(m));
                continue;
            }
            if ("AIRBORNE".equals(m.getStatus()) && round >= m.getRecoverRound()) {
                recoverMission(m);
                changed.add(sortieMissionRepository.save(m));
            }
        }
        return changed;
    }

    private void launchMission(SortieMission mission) {
        CombatUnit unit = combatUnitService.getUnitById(mission.getUnitId());
        if (unit == null || unit.getCombatPower() <= 0) {
            mission.setStatus("ABORTED");
            return;
        }
        if (unit.getFuelLevel() < mission.getFuelCost()) {
            mission.setStatus("ABORTED");
            return;
        }
        String missionType = mission.getMissionType() == null ? "SORTIE" : mission.getMissionType().toUpperCase();
        unit.setFuelLevel(Math.max(0, unit.getFuelLevel() - mission.getFuelCost()));
        unit.setAmmoLevel(Math.max(0, unit.getAmmoLevel() - mission.getAmmoCost()));
        unit.setMission(missionType + ":" + mission.getObjective());
        if ("SEA".equalsIgnoreCase(unit.getDomain())) {
            if ("ANTI_SUBMARINE".equals(missionType)) {
                unit.setNavalTaskType("ANTI_SUBMARINE");
            } else if ("SEA_DENIAL".equals(missionType) || "STRIKE".equals(missionType)) {
                unit.setNavalTaskType("BLOCKADE");
            } else if ("ESCORT".equals(missionType)) {
                unit.setNavalTaskType("ESCORT");
            }
        }
        combatUnitService.updateUnit(unit);
        mission.setStatus("AIRBORNE");
    }

    private void recoverMission(SortieMission mission) {
        CombatUnit unit = combatUnitService.getUnitById(mission.getUnitId());
        if (unit != null) {
            unit.setMission("RECOVERED:" + mission.getMissionName());
            combatUnitService.updateUnit(unit);
        }
        mission.setStatus("COMPLETED");
    }
}
