package com.military.combat.campaign.demo;

import com.military.combat.entity.CombatObjective;

import java.util.ArrayList;
import java.util.List;

/**
 * 淮海战役想定级作战目标（{@link CombatObjective}），与活动建模中的 objectiveId 对应。
 */
public final class HuaihaiScenarioDefaults {

    /** 围歼黄百韬兵团（碾庄方向示意点） */
    public static final String SCN_OBJ_HUANG_BAITAO = "huaihai-scn-obj-huangbaitao";
    /** 围歼黄维兵团（双堆集方向示意点） */
    public static final String SCN_OBJ_HUANG_WEI = "huaihai-scn-obj-huangwei";
    /** 围歼杜聿明集团（陈官庄方向示意点） */
    public static final String SCN_OBJ_DU_YUMING = "huaihai-scn-obj-duyuming";

    private HuaihaiScenarioDefaults() {
    }

    public static List<CombatObjective> scenarioCombatObjectives() {
        List<CombatObjective> list = new ArrayList<>();
        list.add(build(SCN_OBJ_HUANG_BAITAO, "围歼黄百韬兵团", "DESTROY", "RED",
                34.45, 117.78, "歼灭黄百韬兵团于碾庄地区", 1));
        list.add(build(SCN_OBJ_HUANG_WEI, "围歼黄维兵团", "DESTROY", "RED",
                33.85, 116.55, "歼灭黄维兵团于双堆集地区", 2));
        list.add(build(SCN_OBJ_DU_YUMING, "围歼杜聿明集团", "DESTROY", "RED",
                34.05, 116.45, "歼灭杜聿明集团于陈官庄地区", 3));
        return list;
    }

    private static CombatObjective build(String id, String name, String type, String side,
                                         double lat, double lng, String desc, int priority) {
        CombatObjective o = new CombatObjective();
        o.setId(id);
        o.setName(name);
        o.setType(type);
        o.setSide(side);
        o.setLatitude(lat);
        o.setLongitude(lng);
        o.setX(0);
        o.setY(0);
        o.setDescription(desc);
        o.setPriority(priority);
        o.setCompleted(false);
        return o;
    }
}
