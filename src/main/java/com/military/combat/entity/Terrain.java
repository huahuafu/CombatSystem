package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地形实体类
 * 用来定义地图上的特殊区域，比如森林、沼泽、公路
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Terrain {
    private String name;    // 地形名字 ("迷雾森林")
    private String type;    // 类型 (FOREST-森林, SWAMP-沼泽, ROAD-公路)

    // 地形的位置和大小 (百分比 0-100，兼容旧系统)
    private double x;       // 左上角 X 坐标（百分比）
    private double y;       // 左上角 Y 坐标（百分比）
    private double width;   // 宽度（百分比）
    private double height;  // 高度（百分比）
    
    // 真实地理坐标（用于地图系统）
    private Double centerLatitude;   // 中心纬度
    private Double centerLongitude;  // 中心经度
    private Double radius;           // 半径（米）

    // 给前端用的：根据类型返回颜色
    public String getColor() {
        if ("FOREST".equals(type)) return "rgba(46, 204, 113, 0.3)"; // 半透明绿色
        if ("SWAMP".equals(type)) return "rgba(142, 68, 173, 0.3)";  // 半透明紫色
        return "rgba(128, 128, 128, 0.3)"; // 默认灰色
    }
}