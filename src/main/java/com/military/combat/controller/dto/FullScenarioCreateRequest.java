package com.military.combat.controller.dto;

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

    /** 新建想定时一并写入的兵力部署（API 草稿，非 {@link com.military.combat.entity.CombatUnit} 直传） */
    private List<DeploymentUnitDraft> units;

    /** 新建想定时一并写入的作战目标（API 草稿） */
    private List<DeploymentObjectiveDraft> objectives;
}

