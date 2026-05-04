package com.military.combat.controller.dto;

import com.military.combat.entity.CombatObjective;
import com.military.combat.entity.CombatUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 API 层部署草稿转为持久化/推演用的实体（补齐 primitive 默认值）。
 */
public final class ScenarioDraftMapper {

    private ScenarioDraftMapper() {
    }

    public static CombatUnit toCombatUnit(DeploymentUnitDraft d) {
        if (d == null) {
            return null;
        }
        CombatUnit u = new CombatUnit();
        u.setId(d.getId());
        u.setName(d.getName());
        u.setSide(d.getSide());
        u.setType(d.getType());
        u.setMission(d.getMission());
        u.setLatitude(d.getLatitude());
        u.setLongitude(d.getLongitude());
        u.setStatus("ACTIVE");
        u.setCombatPower(100);
        u.setMaxPower(100);
        u.setDomain("SEA");
        if (d.getType() != null) {
            u.setPlatformClass(d.getType());
        }
        return u;
    }

    public static CombatObjective toCombatObjective(DeploymentObjectiveDraft d) {
        if (d == null) {
            return null;
        }
        CombatObjective o = new CombatObjective();
        o.setId(d.getId());
        o.setName(d.getName());
        o.setType(d.getType());
        o.setSide(d.getSide());
        o.setDescription(d.getDescription());
        o.setLatitude(d.getLatitude());
        o.setLongitude(d.getLongitude());
        int p = d.getPriority() != null ? d.getPriority() : 8;
        o.setPriority(Math.max(1, Math.min(10, p)));
        o.setCompleted(false);
        o.setControlThreshold(0.6);
        return o;
    }

    public static List<CombatUnit> mapUnits(List<DeploymentUnitDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<CombatUnit> out = new ArrayList<>(drafts.size());
        for (DeploymentUnitDraft d : drafts) {
            CombatUnit u = toCombatUnit(d);
            if (u != null) {
                out.add(u);
            }
        }
        return out;
    }

    public static List<CombatObjective> mapObjectives(List<DeploymentObjectiveDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) {
            return List.of();
        }
        List<CombatObjective> out = new ArrayList<>(drafts.size());
        for (DeploymentObjectiveDraft d : drafts) {
            CombatObjective o = toCombatObjective(d);
            if (o != null) {
                out.add(o);
            }
        }
        return out;
    }
}
