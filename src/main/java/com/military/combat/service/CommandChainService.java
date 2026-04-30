package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.GroupCommandOrder;
import com.military.combat.repository.GroupCommandOrderRepository;
import com.military.combat.util.NavalDomainValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 编组指挥链服务：命令创建、下发、执行、资源消耗。
 */
@Service
public class CommandChainService {

    @Autowired
    private ScenarioService scenarioService;

    @Autowired
    private CombatUnitService combatUnitService;

    @Autowired
    private GroupCommandOrderRepository orderRepository;

    public List<GroupCommandOrder> listOrders() {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) return new ArrayList<>();
        return orderRepository.findByScenarioId(sid);
    }

    public GroupCommandOrder createOrder(GroupCommandOrder order) {
        String sid = scenarioService.getActiveScenarioId();
        if (sid == null || sid.isEmpty()) throw new IllegalStateException("未激活想定，无法下达命令");
        if (order.getCommanderUnitId() == null || order.getCommanderUnitId().isEmpty()) {
            throw new IllegalArgumentException("commanderUnitId不能为空");
        }
        if (order.getOrderType() == null || order.getOrderType().isEmpty()) {
            throw new IllegalArgumentException("orderType不能为空");
        }
        NavalDomainValidator.normalizeAndValidateCommandOrder(order);
        if (order.getId() == null || order.getId().isEmpty()) order.setId(UUID.randomUUID().toString());
        order.setScenarioId(sid);
        if (order.getStatus() == null) order.setStatus("PLANNED");
        if (order.getIssueRound() <= 0) order.setIssueRound(scenarioService.getCurrentRound());
        if (order.getExpectedFinishRound() <= order.getIssueRound()) order.setExpectedFinishRound(order.getIssueRound() + 2);
        if (order.getFuelBudget() <= 0) order.setFuelBudget(80);
        if (order.getAmmoBudget() <= 0) order.setAmmoBudget(80);
        order.setCreatedAt(System.currentTimeMillis());
        return orderRepository.save(order);
    }

    public List<GroupCommandOrder> runOrdersForCurrentRound() {
        int round = scenarioService.getCurrentRound();
        List<GroupCommandOrder> all = listOrders();
        List<GroupCommandOrder> changed = new ArrayList<>();
        for (GroupCommandOrder o : all) {
            if ("PLANNED".equals(o.getStatus()) && round >= o.getIssueRound()) {
                o.setStatus("ISSUED");
                changed.add(orderRepository.save(o));
                continue;
            }
            if ("ISSUED".equals(o.getStatus()) || "EXECUTING".equals(o.getStatus())) {
                executeOrderOneTick(o);
                if (round >= o.getExpectedFinishRound()) {
                    o.setStatus("COMPLETED");
                } else {
                    o.setStatus("EXECUTING");
                }
                changed.add(orderRepository.save(o));
            }
        }
        return changed;
    }

    private void executeOrderOneTick(GroupCommandOrder order) {
        List<CombatUnit> units = combatUnitService.getUnitsForBattleEngine();
        List<CombatUnit> children = units.stream()
                .filter(u -> u != null && order.getCommanderUnitId().equals(u.getParentUnitId()))
                .collect(Collectors.toList());
        if (children.isEmpty()) {
            CombatUnit commander = combatUnitService.getUnitById(order.getCommanderUnitId());
            if (commander != null) {
                children = List.of(commander);
            } else {
                order.setStatus("FAILED");
                order.setNotes("指挥节点不存在，无法执行");
                return;
            }
        }
        int fuelTick = 0;
        int ammoTick = 0;
        for (CombatUnit unit : children) {
            if (unit == null || unit.getCombatPower() <= 0) continue;
            String type = order.getOrderType() == null ? "" : order.getOrderType().toUpperCase();
            int thisFuel;
            switch (type) {
                case "ISR":
                    thisFuel = 4;
                    break;
                case "EW":
                case "ELECTRONIC_SUPPRESSION":
                    thisFuel = 6;
                    break;
                case "ROUTE_MANEUVER":
                case "ESCORT":
                    thisFuel = 7;
                    break;
                case "WITHDRAW_REORGANIZE":
                    thisFuel = 3;
                    break;
                default:
                    thisFuel = 8;
                    break;
            }
            int thisAmmo;
            switch (type) {
                case "STRIKE":
                case "FIRE_ALLOCATION":
                    thisAmmo = 10;
                    break;
                case "AIR_DEFENSE":
                    thisAmmo = 7;
                    break;
                case "WITHDRAW_REORGANIZE":
                case "ROUTE_MANEUVER":
                    thisAmmo = 1;
                    break;
                default:
                    thisAmmo = 3;
                    break;
            }
            unit.setFuelLevel(Math.max(0, unit.getFuelLevel() - thisFuel));
            unit.setAmmoLevel(Math.max(0, unit.getAmmoLevel() - thisAmmo));
            unit.setMission("CMD-" + type + ":" + (order.getObjective() == null ? "" : order.getObjective()));
            if ("SEA".equalsIgnoreCase(unit.getDomain())) {
                if ("ROUTE_MANEUVER".equals(type)) {
                    unit.setNavalTaskType("SEA_CONTROL");
                } else if ("ESCORT".equals(type)) {
                    unit.setNavalTaskType("ESCORT");
                    unit.setFormationRole("SCREEN");
                } else if ("FIRE_ALLOCATION".equals(type)) {
                    unit.setNavalTaskType("STRIKE");
                    unit.setDetectionChainStatus("OPEN");
                } else if ("ELECTRONIC_SUPPRESSION".equals(type)) {
                    unit.setNavalTaskType("ISR");
                    unit.setDetectionChainStatus("DEGRADED");
                } else if ("WITHDRAW_REORGANIZE".equals(type)) {
                    unit.setNavalTaskType("RESUPPLY");
                    unit.setSupplyStatus("LOW");
                }
            }
            combatUnitService.updateUnit(unit);
            fuelTick += thisFuel;
            ammoTick += thisAmmo;
        }
        order.setUsedFuel(Math.min(order.getFuelBudget(), order.getUsedFuel() + fuelTick));
        order.setUsedAmmo(Math.min(order.getAmmoBudget(), order.getUsedAmmo() + ammoTick));
    }
}
