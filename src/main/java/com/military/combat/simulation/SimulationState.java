package com.military.combat.simulation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端与「自定义作战活动推演」相关的统一快照（单入口，避免多处接口语义不一致）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationState {

    /** 服务器当前回合（与 {@link com.military.combat.service.ScenarioService#getCurrentRound} 一致） */
    private int round;

    /** 当前工作想定 ID，未设置则为 null */
    private String activeScenarioId;

    private SimulationMode mode;

    /** 与 mode == TASK_DRIVEN 同义，便于旧前端字段兼容 */
    private boolean taskDriven;

    /** 人类可读说明 */
    private String modeDescription;

    /** 会话中的战役 ID，无则为 null */
    private String sessionCampaignId;

    /** 会话中的战役名称，无则为 null */
    private String sessionCampaignName;

    /**
     * 战役主线一句话：战役 → 想定兵力部署 → 活动命令 → 交互规则（见 {@link com.military.combat.battleline.BattlelineService}）。
     */
    private String mainLine;

    /** 当前作战条令（用于强调后端执行逻辑，而非前端看板）。 */
    private String doctrine;

    /** 当前主导作战阶段（六阶段之一）。 */
    private String operationalPhase;

    /** 当前主导作战阶段中文名。 */
    private String operationalPhaseName;

    /** 当前判定胜方（RED/BLUE/DRAW），未决则为 null。 */
    private String winner;

    /** 当前判胜原因。 */
    private String winReason;

    /**
     * 杀伤链逐步模式下，下一次「推进」对应的片段序号（0=FIND … 5=ASSESS）。
     */
    private int killChainSequentialStep;
}
