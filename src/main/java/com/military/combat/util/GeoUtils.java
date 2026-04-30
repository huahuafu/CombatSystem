package com.military.combat.util;

/**
 * 地理坐标工具类
 * 用于计算真实地理坐标之间的距离和移动
 */
public class GeoUtils {
    
    private static final double EARTH_RADIUS = 6371000; // 地球半径（米）
    
    /**
     * 计算两点间的真实地理距离（米）
     * 使用Haversine公式
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }
    
    /**
     * 从起点向目标点移动指定距离（米），返回新坐标
     */
    public static double[] moveTowards(double lat1, double lon1, double lat2, double lon2, double distance) {
        double totalDistance = calculateDistance(lat1, lon1, lat2, lon2);
        if (totalDistance == 0 || distance >= totalDistance) {
            return new double[]{lat2, lon2};
        }
        
        double ratio = distance / totalDistance;
        double newLat = lat1 + (lat2 - lat1) * ratio;
        double newLon = lon1 + (lon2 - lon1) * ratio;
        
        return new double[]{newLat, newLon};
    }
    
    /**
     * 计算方位角（度）
     */
    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        
        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }
}

