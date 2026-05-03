package com.military.combat.service;

import com.military.combat.entity.Campaign;
import com.military.combat.entity.CampaignEvent;
import com.military.combat.entity.CampaignObjective;
import com.military.combat.entity.CombatActivity;
import com.military.combat.entity.CombatUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 战役会话状态与推演中的阶段/目标/事件检查 —— <strong>主线第一层：自定义战役</strong>（阶段、战役目标与关键事件）。
 * 正式使用以想定内嵌战役或 {@code applyCampaignToSession} 为准。
 */
@Service
public class CampaignService {
    private Campaign currentCampaign;
    private int currentPhase;

    /**
     * 为战役及下属目标/事件补全稳定 ID（自定义战役与想定保存时必调）。
     */
    public void normalizeCampaign(Campaign c) {
        if (c == null) {
            return;
        }
        if (c.getId() == null || c.getId().trim().isEmpty()) {
            c.setId(UUID.randomUUID().toString());
        }
        if (c.getPhases() == null) {
            c.setPhases(new ArrayList<>());
        }
        if (c.getObjectives() != null) {
            for (CampaignObjective o : c.getObjectives()) {
                if (o != null && (o.getId() == null || o.getId().trim().isEmpty())) {
                    o.setId(UUID.randomUUID().toString());
                }
            }
        }
        if (c.getEvents() != null) {
            for (CampaignEvent e : c.getEvents()) {
                if (e != null && (e.getId() == null || e.getId().trim().isEmpty())) {
                    e.setId(UUID.randomUUID().toString());
                }
            }
        }
    }

    /**
     * 将战役设为当前推演会话（含自定义战役、想定内嵌战役）。
     */
    public Campaign applyCampaignToSession(Campaign c) {
        if (c == null) {
            return null;
        }
        normalizeCampaign(c);
        this.currentCampaign = c;
        this.currentPhase = 0;
        return c;
    }

    /**
     * 清除当前会话战役（不删除想定数据）。
     */
    public void clearSessionCampaign() {
        this.currentCampaign = null;
        this.currentPhase = 0;
    }

    /**
     * 获取当前战役
     */
    public Campaign getCurrentCampaign() {
        return currentCampaign;
    }
    
    /**
     * 获取当前战役阶段
     */
    public int getCurrentPhase() {
        return currentPhase;
    }
    
    /**
     * 设置当前战役阶段
     */
    public void setCurrentPhase(int phase) {
        this.currentPhase = phase;
    }
    
    public void advancePhase() {
        if (currentCampaign != null && currentPhase < currentCampaign.getPhases().size() - 1) {
            currentPhase++;
        }
    }

    /**
     * 与推演引擎共用：关键事件是否满足触发条件（不修改事件状态）。
     */
    public boolean isCampaignEventTriggered(CampaignEvent event, List<CombatUnit> units) {
        if (event == null || units == null) {
            return false;
        }
        return evaluateConditionExpression(event.getTriggerCondition(), units);
    }

    /**
     * 与推演引擎共用：战役目标是否从当前战场态势判定为达成。
     */
    public boolean isCampaignObjectiveDone(CampaignObjective objective, List<CombatUnit> units) {
        if (objective == null || units == null || objective.getTargetId() == null) {
            return false;
        }
        String type = objective.getType();
        String tid = objective.getTargetId();
        if ("DESTROY".equals(type)) {
            return evaluateTargetEliminated(tid, units);
        }
        if ("DEFEND".equals(type)) {
            return evaluateTargetAlive(tid, units);
        }
        if ("CAPTURE".equals(type)) {
            // CAPTURE 默认由条件表达式驱动；若 targetId 不是表达式，则按目标“存活存在”近似判定
            if (evaluateConditionExpression(tid, units)) {
                return true;
            }
            return evaluateTargetAlive(tid, units);
        }
        return evaluateConditionExpression(tid, units);
    }

    /**
     * 条件表达式（去硬编码）：
     * - SIDE_ALIVE_LE:BLUE:3
     * - SIDE_ALIVE_GE:RED:5
     * - UNIT_HP_BELOW:name:某部队:50
     * - UNIT_HP_BELOW:id:unit-1:60
     * - UNIT_EXISTS:name:某部队
     * - UNIT_ELIMINATED:name:某部队
     * - UNIT_IN_AREA:BLUE:38.0:38.8:127.0:128.0
     */
    private boolean evaluateConditionExpression(String expr, List<CombatUnit> units) {
        if (expr == null || expr.trim().isEmpty()) {
            return false;
        }
        String[] p = expr.split(":");
        String op = p[0];
        if ("SIDE_ALIVE_LE".equals(op) && p.length >= 3) {
            long alive = units.stream().filter(u -> p[1].equals(u.getSide()) && u.getCombatPower() > 0).count();
            return alive <= parseIntSafe(p[2], Integer.MIN_VALUE);
        }
        if ("SIDE_ALIVE_GE".equals(op) && p.length >= 3) {
            long alive = units.stream().filter(u -> p[1].equals(u.getSide()) && u.getCombatPower() > 0).count();
            return alive >= parseIntSafe(p[2], Integer.MAX_VALUE);
        }
        if ("UNIT_HP_BELOW".equals(op) && p.length >= 4) {
            int hp = parseIntSafe(p[3], Integer.MIN_VALUE);
            return units.stream().anyMatch(u -> matchesUnitSelector(u, p[1], p[2]) && u.getCombatPower() < hp);
        }
        if ("UNIT_EXISTS".equals(op) && p.length >= 3) {
            return units.stream().anyMatch(u -> matchesUnitSelector(u, p[1], p[2]) && u.getCombatPower() > 0);
        }
        if ("UNIT_ELIMINATED".equals(op) && p.length >= 3) {
            return units.stream().noneMatch(u -> matchesUnitSelector(u, p[1], p[2]) && u.getCombatPower() > 0);
        }
        if ("UNIT_IN_AREA".equals(op) && p.length >= 6) {
            String side = p[1];
            double minLat = parseDoubleSafe(p[2], Double.MAX_VALUE);
            double maxLat = parseDoubleSafe(p[3], -Double.MAX_VALUE);
            double minLon = parseDoubleSafe(p[4], Double.MAX_VALUE);
            double maxLon = parseDoubleSafe(p[5], -Double.MAX_VALUE);
            return units.stream().anyMatch(u -> side.equals(u.getSide()) && u.getCombatPower() > 0
                    && u.getLatitude() != null && u.getLongitude() != null
                    && u.getLatitude() >= minLat && u.getLatitude() <= maxLat
                    && u.getLongitude() >= minLon && u.getLongitude() <= maxLon);
        }
        return false;
    }

    private boolean evaluateTargetEliminated(String targetId, List<CombatUnit> units) {
        if (targetId == null || targetId.isEmpty()) {
            return false;
        }
        if (evaluateConditionExpression(targetId, units)) {
            return true;
        }
        if (targetId.startsWith("id:")) {
            String id = targetId.substring(3);
            return units.stream().noneMatch(u -> id.equals(u.getId()) && u.getCombatPower() > 0);
        }
        if (targetId.startsWith("name:")) {
            String key = targetId.substring(5);
            return units.stream().noneMatch(u -> u.getName() != null && u.getName().contains(key) && u.getCombatPower() > 0);
        }
        return units.stream().noneMatch(u -> targetId.equals(u.getId()) && u.getCombatPower() > 0);
    }

    private boolean evaluateTargetAlive(String targetId, List<CombatUnit> units) {
        if (targetId == null || targetId.isEmpty()) {
            return false;
        }
        if (evaluateConditionExpression(targetId, units)) {
            return true;
        }
        if (targetId.startsWith("id:")) {
            String id = targetId.substring(3);
            return units.stream().anyMatch(u -> id.equals(u.getId()) && u.getCombatPower() > 0);
        }
        if (targetId.startsWith("name:")) {
            String key = targetId.substring(5);
            return units.stream().anyMatch(u -> u.getName() != null && u.getName().contains(key) && u.getCombatPower() > 0);
        }
        return units.stream().anyMatch(u -> targetId.equals(u.getId()) && u.getCombatPower() > 0);
    }

    private boolean matchesUnitSelector(CombatUnit u, String selectorType, String selectorValue) {
        if (u == null || selectorType == null) {
            return false;
        }
        if ("id".equals(selectorType)) {
            return selectorValue != null && selectorValue.equals(u.getId());
        }
        if ("name".equals(selectorType)) {
            return selectorValue != null && u.getName() != null && u.getName().contains(selectorValue);
        }
        if ("side".equals(selectorType)) {
            return selectorValue != null && selectorValue.equals(u.getSide());
        }
        return false;
    }

    private int parseIntSafe(String v, int fallback) {
        try {
            return Integer.parseInt(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    private double parseDoubleSafe(String v, double fallback) {
        try {
            return Double.parseDouble(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 手动「检查事件」：仅当条件满足时标记 triggered（与推演逻辑一致） */
    public void checkEvents(List<CombatUnit> units) {
        if (currentCampaign == null || currentCampaign.getPhases() == null || currentCampaign.getEvents() == null) {
            return;
        }
        if (currentPhase < 0 || currentPhase >= currentCampaign.getPhases().size()) {
            return;
        }
        String phaseName = currentCampaign.getPhases().get(currentPhase);
        for (CampaignEvent event : currentCampaign.getEvents()) {
            if (event.isTriggered()) {
                continue;
            }
            if (!phaseName.equals(event.getPhase())) {
                continue;
            }
            if (isCampaignEventTriggered(event, units)) {
                event.setTriggered(true);
                System.out.println("事件触发: " + event.getName());
            }
        }
    }

    /** 手动「检查完成」辅助：按战场态势更新各项目标 completed */
    public void checkObjectives(List<CombatUnit> units) {
        if (currentCampaign == null || currentCampaign.getPhases() == null || currentCampaign.getObjectives() == null) {
            return;
        }
        if (currentPhase < 0 || currentPhase >= currentCampaign.getPhases().size()) {
            return;
        }
        String phaseName = currentCampaign.getPhases().get(currentPhase);
        for (CampaignObjective objective : currentCampaign.getObjectives()) {
            if (objective.isCompleted()) {
                continue;
            }
            if (!phaseName.equals(objective.getPhase())) {
                continue;
            }
            if (isCampaignObjectiveDone(objective, units)) {
                objective.setCompleted(true);
                System.out.println("目标完成: " + objective.getName());
            }
        }
    }

    public boolean isCampaignComplete() {
        if (currentCampaign == null) return false;

        return currentCampaign.getObjectives().stream()
                .allMatch(CampaignObjective::isCompleted);
    }

    /**
     * 更新战役目标完成状态
     * @param objectiveId 目标ID
     * @param completed 是否完成
     */
    public void updateObjectiveStatus(String objectiveId, boolean completed) {
        if (currentCampaign == null) return;
        
        for (CampaignObjective objective : currentCampaign.getObjectives()) {
            if (objective.getId().equals(objectiveId)) {
                objective.setCompleted(completed);
                System.out.println("战役目标状态更新: " + objective.getName() + " -> " + (completed ? "完成" : "未完成"));
                break;
            }
        }
    }

    /**
     * 根据活动结果检查并更新战役目标
     * @param activity 作战活动
     */
    public void checkAndUpdateObjectivesByActivity(CombatActivity activity) {
        if (currentCampaign == null || activity == null) return;
        if (!"COMPLETED".equals(activity.getStatus())) return;

        String targetId = activity.getCampaignObjectiveId();
        if ((targetId == null || targetId.isEmpty()) && activity.getCampaignObjectiveLabel() != null) {
            String label = activity.getCampaignObjectiveLabel().trim();
            if (!label.isEmpty() && currentCampaign.getObjectives() != null) {
                for (CampaignObjective o : currentCampaign.getObjectives()) {
                    if (o != null && (label.equals(o.getName()) || label.equals(o.getId()))) {
                        targetId = o.getId();
                        break;
                    }
                }
            }
        }
        if (targetId != null && !targetId.isEmpty()) {
            updateObjectiveStatus(targetId, true);
        }

        if (areAllPhaseObjectivesCompleted()) {
            advancePhase();
        }
    }

    /**
     * 检查当前阶段的所有目标是否都已完成
     */
    public boolean areAllPhaseObjectivesCompleted() {
        if (currentCampaign == null) return false;
        
        String currentPhaseName = currentCampaign.getPhases().get(currentPhase);
        return currentCampaign.getObjectives().stream()
                .filter(obj -> obj.getPhase().equals(currentPhaseName))
                .allMatch(CampaignObjective::isCompleted);
    }

    /**
     * 检查关键事件触发条件
     * @param activity 作战活动
     * @return 触发的事件列表
     */
    public List<CampaignEvent> checkEventTriggers(CombatActivity activity) {
        List<CampaignEvent> triggeredEvents = new ArrayList<>();
        if (currentCampaign == null || activity == null) return triggeredEvents;
        
        for (CampaignEvent event : currentCampaign.getEvents()) {
            if (!event.isTriggered() && shouldTriggerEvent(event, activity)) {
                event.setTriggered(true);
                triggeredEvents.add(event);
                System.out.println("关键事件触发: " + event.getName());
            }
        }
        return triggeredEvents;
    }

    /**
     * 判断是否应该触发事件
     */
    private boolean shouldTriggerEvent(CampaignEvent event, CombatActivity activity) {
        // 检查事件是否与活动关联
        if (activity.getTriggerEventIds() != null && activity.getTriggerEventIds().contains(event.getId())) {
            // 检查活动是否成功完成
            if (activity.getStatus().equals("COMPLETED")) {
                // 检查是否在当前阶段
                String currentPhaseName = currentCampaign.getPhases().get(currentPhase);
                return event.getPhase().equals(currentPhaseName);
            }
        }
        return false;
    }

    /**
     * 获取当前阶段的战役目标
     */
    public List<CampaignObjective> getCurrentPhaseObjectives() {
        if (currentCampaign == null) return new ArrayList<>();
        
        String currentPhaseName = currentCampaign.getPhases().get(currentPhase);
        List<CampaignObjective> phaseObjectives = new ArrayList<>();
        for (CampaignObjective objective : currentCampaign.getObjectives()) {
            if (objective.getPhase().equals(currentPhaseName)) {
                phaseObjectives.add(objective);
            }
        }
        return phaseObjectives;
    }

    /**
     * 获取当前阶段的关键事件
     */
    public List<CampaignEvent> getCurrentPhaseEvents() {
        if (currentCampaign == null) return new ArrayList<>();
        
        String currentPhaseName = currentCampaign.getPhases().get(currentPhase);
        List<CampaignEvent> phaseEvents = new ArrayList<>();
        for (CampaignEvent event : currentCampaign.getEvents()) {
            if (event.getPhase().equals(currentPhaseName)) {
                phaseEvents.add(event);
            }
        }
        return phaseEvents;
    }

}