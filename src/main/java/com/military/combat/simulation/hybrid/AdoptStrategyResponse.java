package com.military.combat.simulation.hybrid;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采纳 AI 策略后的响应（活动/规则落库结果）。
 */
@Data
public class AdoptStrategyResponse {
    private String scenarioId;
    private String adoptedStrategyId;
    private String adoptedStrategyName;
    private String message;
    private List<String> createdActivities = new ArrayList<>();
    private List<String> createdRules = new ArrayList<>();
    private boolean success = true;
}
