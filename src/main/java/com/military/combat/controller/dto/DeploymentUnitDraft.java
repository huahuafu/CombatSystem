package com.military.combat.controller.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 指挥官端 / 想定创建 API 传入的兵力部署草稿。
 * <p>仅包含地图标绘与校验所需字段，避免反序列化到 {@link com.military.combat.entity.CombatUnit} 时
 * Jackson 3 对 primitive 的严格限制；持久化前由 {@link ScenarioDraftMapper} 转为实体。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentUnitDraft {

    private String id;
    private String name;
    private String side;
    private String type;
    private String mission;

    private Double latitude;
    private Double longitude;
}
