package com.military.combat.simulation.commander;

import lombok.Data;

/**
 * 阶段动作（结构化：用于 AI 输出与审计）。
 */
@Data
public class KillChainStageAction {
    private KillChainStageKey stage;
    private String actionType;
    private String title;
    private String detail;
    private double intensity; // 0~1 作为统一刻度
}

