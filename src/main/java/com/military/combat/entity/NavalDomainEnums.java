package com.military.combat.entity;

/**
 * 海上作战域枚举集合（兼容现有字符串字段，作为值域约束与前后端约定）。
 */
public final class NavalDomainEnums {

    private NavalDomainEnums() {
    }

    public enum VesselType {
        CARRIER,
        DESTROYER,
        FRIGATE,
        CORVETTE,
        SUBMARINE,
        AMPHIBIOUS_SHIP,
        SUPPORT_SHIP,
        PATROL_SHIP,
        UAV,
        MPA
    }

    public enum TaskType {
        SEA_CONTROL,
        ESCORT,
        BLOCKADE,
        STRIKE,
        ISR,
        ANTI_SUBMARINE,
        AIR_DEFENSE,
        RESUPPLY
    }

    public enum SupplyStatus {
        FULL,
        ADEQUATE,
        LOW,
        CRITICAL,
        EXHAUSTED
    }

    public enum DetectionChainStatus {
        OPEN,
        DEGRADED,
        JAMMED,
        LOST
    }

    public enum ObjectiveType {
        MISSION_TARGET,
        AREA_CONTROL,
        ESCORT,
        BLOCKADE
    }

    public enum CommandType {
        ROUTE_MANEUVER,
        ESCORT,
        FIRE_ALLOCATION,
        ELECTRONIC_SUPPRESSION,
        WITHDRAW_REORGANIZE
    }
}
