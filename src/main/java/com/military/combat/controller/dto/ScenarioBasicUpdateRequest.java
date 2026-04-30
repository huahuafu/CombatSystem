package com.military.combat.controller.dto;

import lombok.Data;

/**
 * 想定管理：仅更新基础字段，避免覆盖 units/objectives 等复杂结构。
 */
@Data
public class ScenarioBasicUpdateRequest {
    private String id;
    private String name;
    private String description;
    private Integer maxRounds;
}

