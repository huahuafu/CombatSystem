package com.military.combat.controller.dto;

import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;
import lombok.Data;

import java.util.List;

@Data
public class FullScenarioCreateRequest {
    private String name;
    private String description;
    private String goal;

    /** 海域中心点（用于默认目标与地图定位） */
    private Double centerLatitude;
    private Double centerLongitude;

    /** 最大回合数（可选） */
    private Integer maxRounds;

    /** 新建想定时一并写入的兵力部署（草稿清单） */
    private List<CombatUnit> units;

    /** 新建想定时一并写入的作战目标（草稿清单） */
    private List<CombatObjective> objectives;
}

