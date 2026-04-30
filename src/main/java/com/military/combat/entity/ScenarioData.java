package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 作战想定数据模型 —— <strong>主线第二层：自定义战役下的兵力部署与战场态势</strong>。
 * 与 {@link #campaign} / {@link #campaignId} 绑定后，活动建模与交互规则均挂在此想定之下。
 */
@Data
@Document(collection = "scenario_data")
public class ScenarioData {
    @Id
    private String id;
    
    private String name;                    // 想定名称
    private String description;             // 想定描述

    /**
     * 所属自定义战役 ID（与内嵌 {@link #campaign}{@code .id} 一致，便于按战役查询；保存想定时自动同步）。
     */
    private String campaignId;
    private String createTime;              // 创建时间
    
    // 红蓝双方信息
    private String redSideName;             // 红方名称
    private String blueSideName;            // 蓝方名称
    
    // 作战单位列表
    private List<CombatUnit> units;         // 所有作战单位
    
    // 作战目标列表
    private List<CombatObjective> objectives; // 作战目标
    
    // 地形信息
    private List<Terrain> terrains;         // 地形列表
    
    // 作战配置
    private int maxRounds;                  // 最大回合数
    private String victoryCondition;        // 胜利条件
    private String scenarioDomain;          // LAND/SEA/AIR...
    private String evaluationProfile;       // 标准评估模板（如 NAVAL_BALANCED）
    private String winner;                  // 当前判定胜方（RED/BLUE/DRAW）
    private String winReason;               // 胜利原因（目标达成/舰队歼灭/回合结束判定）

    /** 战役模板快照（saveType 为 CAMPAIGN 时使用，便于「管理」中展示阶段/事件/目标而无需再次全量加载） */
    private Campaign campaign;
    
    // 保存类型（合并存档功能）
    private String saveType;                // 保存类型：TEMPLATE（模板/初始配置）或 PROGRESS（进度/当前状态）
    private Integer currentRound;            // 当前回合数（进度保存时使用）
    private List<RoundStat> stats;          // 统计数据（进度保存时使用）
}

