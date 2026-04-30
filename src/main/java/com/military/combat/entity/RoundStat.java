package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回合统计数据 (用于画图)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoundStat {
    private int round;          // 第几回合
    private int redTotalHp;     // 红方总血量
    private int blueTotalHp;    // 蓝方总血量
    private int redCount;       // 红方存活数量
    private int blueCount;      // 蓝方存活数量
}