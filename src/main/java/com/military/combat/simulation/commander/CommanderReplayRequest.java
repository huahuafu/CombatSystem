package com.military.combat.simulation.commander;

import lombok.Data;

/**
 * 回放请求：默认复用历史 run 的 seed/label/strategyId/scenarioId/goal。
 * 可选覆盖 rounds 与 seed（用于“同策略不同回合”或“变 seed 复现实验”）。
 */
@Data
public class CommanderReplayRequest {
    private Integer rounds;
    private Long seed;
    /** 为 true 时将本次回放也落库为新的 run 记录（默认 false，仅返回结果）。 */
    private boolean persist = false;
}

