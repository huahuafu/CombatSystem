package com.military.combat.simulation.commander;

/**
 * 指挥官策略四选一：固定语义。
 */
public enum CommanderStrategyLabel {
    WIN_MAX,
    LOSS_MIN,
    SPEED_MAX,
    BALANCED,
    // backward compatibility for historical run records
    A_LOSS_MIN,
    B_WINRATE_MAX,
    C_BALANCED
}

