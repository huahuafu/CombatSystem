package com.military.combat.entity;
import lombok.Data;

@Data
public class BattleEvent {
    private String source;    // 谁发起的 (如: 红军T-90)

    // ↓↓↓↓↓↓↓↓↓ 新增这个字段 ↓↓↓↓↓↓↓↓↓
    private String side;      // 发起者的阵营 (RED / BLUE)
    // ↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑↑

    private String target;    // 谁被打
    private String action;    // 动作 (ATTACK, MOVE, DESTROY)
    private int damage;       // 伤害值
    private String message;   // 描述文本
}