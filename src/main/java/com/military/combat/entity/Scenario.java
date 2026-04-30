package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * @deprecated 已由 {@link ScenarioData} 取代，主线推演仅使用 scenario_data。
 * 该实体仅保留用于历史数据读取或一次性迁移，不再承载新功能。
 */
@Deprecated
@Data
@Document(collection = "scenarios")
public class Scenario {
    @Id
    private String id;              // 存档ID
    private String name;            // 存档名称
    private String createTime;      // 保存时间

    private int currentRound;       // 保存时的回合数
    private List<CombatUnit> units; // 保存那一刻的所有单位状态
    private List<RoundStat> stats;  // 保存那一刻的图表数据
}