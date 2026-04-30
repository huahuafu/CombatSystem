package com.military.combat.entity;

import java.util.Map;

public class CampaignEvent {
    private String id;
    private String name; // 事件名称
    private String description; // 事件描述
    private String phase; // 所属阶段
    private String triggerCondition; // 触发条件
    private Map<String, Object> effects; // 事件效果
    private boolean triggered; // 是否已触发

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getTriggerCondition() { return triggerCondition; }
    public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }
    public Map<String, Object> getEffects() { return effects; }
    public void setEffects(Map<String, Object> effects) { this.effects = effects; }
    public boolean isTriggered() { return triggered; }
    public void setTriggered(boolean triggered) { this.triggered = triggered; }
}
