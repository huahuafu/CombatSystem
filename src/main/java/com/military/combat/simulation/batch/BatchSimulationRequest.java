package com.military.combat.simulation.batch;

import lombok.Data;

/**
 * 同想定连续多轮「批量模拟」请求体。
 */
@Data
public class BatchSimulationRequest {
    /**
     * 想定 ID；空则使用服务端当前激活想定。
     */
    private String scenarioId;
    /**
     * 独立运行次数（每次从想定兵力重新部署并推进若干回合）。
     */
    private int runs = 3;
    /**
     * 每次运行推进的回合数。
     */
    private int roundsPerRun = 20;
    /**
     * 计分视角：RED 时蓝方战损加分、红方战损减分；BLUE 时相反。
     */
    private String scorePerspective = "RED";
    /**
     * 可选：批量运行的 base seed。为空时服务端自动生成，并在响应中返回。
     * <p>每个 run 的 seed = mix(baseSeed, runIndex)。</p>
     */
    private Long seed;
    /**
     * 为 true 时，在第一次重放前删除本想定下名称以 {@code [AI]} 开头的作战活动与交互规则，与 AI 自动建模的「替换旧条」策略一致，减少旧规则干扰对局。
     */
    private boolean replaceAiArtifacts = false;
}
