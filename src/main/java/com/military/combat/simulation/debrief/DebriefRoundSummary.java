package com.military.combat.simulation.debrief;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 前端从 RoundInfoDigest 压缩后的回合摘要，用于战后简报（避免传输大体量探测明细）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebriefRoundSummary {
    private int round;
    private double infoAdvantage;
    private double datalinkIntegrity;
    /** 指挥侧己方战力占比 0–1 */
    private double ownCombatShare;
    private int interactionCount;
    private int redHp;
    private int blueHp;
    /** 后端 RoundStat.round，无则为 -1 */
    private int statRound;
}
