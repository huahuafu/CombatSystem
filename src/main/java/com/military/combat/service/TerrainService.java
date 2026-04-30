package com.military.combat.service;

import com.military.combat.entity.CombatUnit;
import com.military.combat.entity.Terrain;
import com.military.combat.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TerrainService {

    // 内存中的地形数据
    private List<Terrain> terrainList = new ArrayList<>();

    public TerrainService() {
        // 初始化地形（使用百分比坐标，地理坐标为null）
        terrainList.add(new Terrain("迷雾森林", "FOREST", 20.0, 20.0, 40.0, 40.0, null, null, null));
        terrainList.add(new Terrain("死亡沼泽", "SWAMP", 70.0, 70.0, 25.0, 25.0, null, null, null));
    }

    public List<Terrain> getTerrains() {
        return terrainList;
    }

    public void addTerrain(Terrain terrain) {
        terrainList.add(terrain);
    }

    public void removeTerrain(String name) {
        terrainList.removeIf(t -> t.getName().equals(name));
    }

    /**
     * 获取单位所在的地形
     */
    public Terrain getUnitTerrain(CombatUnit unit) {
        // 优先使用真实地理坐标检测
        if (unit.getLatitude() != null && unit.getLongitude() != null) {
            for (Terrain t : terrainList) {
                if (t.getCenterLatitude() != null && t.getCenterLongitude() != null && t.getRadius() != null) {
                    double dist = GeoUtils.calculateDistance(
                        unit.getLatitude(), unit.getLongitude(),
                        t.getCenterLatitude(), t.getCenterLongitude()
                    );
                    if (dist <= t.getRadius()) {
                        return t;
                    }
                }
            }
        }
        // 回退到百分比坐标检测
        for (Terrain t : terrainList) {
            if (unit.getX() >= t.getX() && unit.getX() <= t.getX() + t.getWidth() &&
                    unit.getY() >= t.getY() && unit.getY() <= t.getY() + t.getHeight()) return t;
        }
        return null;
    }

    /**
     * 获取地形对移动速度的影响
     */
    public double getTerrainSpeedModifier(Terrain terrain) {
        if (terrain == null) return 1.0;
        
        switch (terrain.getType()) {
            case "FOREST":
                return 0.5;
            case "SWAMP":
                return 0.2;
            case "MOUNTAIN":
                return 0.3;
            case "RIVER":
                return 0.4;
            default:
                return 1.0;
        }
    }

    /**
     * 获取地形对防御的影响
     */
    public double getTerrainDefenseModifier(Terrain terrain) {
        if (terrain == null) return 1.0;
        
        switch (terrain.getType()) {
            case "FOREST":
                return 0.6;
            case "SWAMP":
                return 1.2;
            case "MOUNTAIN":
                return 0.5;
            default:
                return 1.0;
        }
    }
}
