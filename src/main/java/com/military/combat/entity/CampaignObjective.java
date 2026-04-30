package com.military.combat.entity;

public class CampaignObjective {
    private String id;
    private String name; // 目标名称
    private String description; // 目标描述
    private String side; // 所属方
    private String phase; // 所属阶段
    private String type; // 目标类型（占领、摧毁、防御等）
    private String targetId; // 目标ID
    private boolean completed; // 是否完成
    private int priority; // 优先级

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
}
