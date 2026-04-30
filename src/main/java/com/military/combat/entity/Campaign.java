package com.military.combat.entity;

import java.util.List;
import java.util.Map;

public class Campaign {
    /** 稳定标识：预设战役为固定字符串（如 huaihai）；自定义战役在保存想定或 POST /campaign/custom 时由服务端补全 UUID */
    private String id;
    private String name; // 战役名称
    private String description; // 战役描述
    private String startDate; // 开始日期
    private String endDate; // 结束日期
    private List<String> phases; // 战役阶段
    private Map<String, String> sides; // 参战方
    private List<CampaignEvent> events; // 关键事件
    private List<CampaignObjective> objectives; // 战役目标
    private Map<String, Object> terrainSettings; // 地形设置
    private Map<String, Object> rules; // 战役规则

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<String> getPhases() { return phases; }
    public void setPhases(List<String> phases) { this.phases = phases; }
    public Map<String, String> getSides() { return sides; }
    public void setSides(Map<String, String> sides) { this.sides = sides; }
    public List<CampaignEvent> getEvents() { return events; }
    public void setEvents(List<CampaignEvent> events) { this.events = events; }
    public List<CampaignObjective> getObjectives() { return objectives; }
    public void setObjectives(List<CampaignObjective> objectives) { this.objectives = objectives; }
    public Map<String, Object> getTerrainSettings() { return terrainSettings; }
    public void setTerrainSettings(Map<String, Object> terrainSettings) { this.terrainSettings = terrainSettings; }
    public Map<String, Object> getRules() { return rules; }
    public void setRules(Map<String, Object> rules) { this.rules = rules; }
}
