package com.military.combat.simulation;

/**
 * 单回合推演在引擎中的工作方式（由是否存在「本回合应执行的作战活动」决定）。
 */
public enum SimulationMode {

    /**
     * 本回合没有落在时间窗内、且通过想定过滤的作战活动：全场单位按原逻辑自主寻敌交战。
     */
    FREE_FOR_ALL,

    /**
     * 本回合存在上述作战活动：仅执行活动步骤（及战役/协同），不执行全场自主交战。
     */
    TASK_DRIVEN
}
