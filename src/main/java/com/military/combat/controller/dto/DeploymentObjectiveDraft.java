package com.military.combat.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 指挥官端 / 想定创建 API 传入的作战目标草稿。
 * <p>与 {@link com.military.combat.entity.CombatObjective} 分离，避免 API JSON 与 Mongo 文档字段一一绑定。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentObjectiveDraft {

    private String id;
    private String name;
    private String type;
    private String side;
    private String description;

    private Double latitude;
    private Double longitude;

    private Integer priority;
}
