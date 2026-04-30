package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 武器装备实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Weapon {
    private String name;           // 武器名称
    private String type;          // 武器类型 (RIFLE, CANNON, MISSILE, etc.)
    private int damage;            // 基础伤害
    private double range;          // 射程（米）
    private double accuracy;       // 精度（0-1）
    private int ammo;              // 弹药数量
    private String description;    // 描述
}

