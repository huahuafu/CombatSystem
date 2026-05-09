package com.military.combat.simulation.hybrid;

import lombok.Data;

/**
 * 采纳 AI 推荐策略的请求（阶段 C adopt 端点）。
 */
@Data
public class AdoptStrategyRequest {
    private String scenarioId;
    private String strategyId;
    private String strategyName;
    /** 前端选中的 planJson（AI 返回的详细计划） */
    private String planJson;
    private String goal;
}
