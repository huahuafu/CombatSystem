package com.military.combat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 传感器模型：用于平台探测与识别能力建模。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Sensor {
    private String name;                 // 传感器名称
    private String type;                 // RADAR/EOIR/ELINT/SONAR/SAR
    private double detectionRange;       // 探测距离（米）
    private double trackingRange;        // 稳定跟踪距离（米）
    private double identificationRange;  // 识别距离（米）
    private double refreshSeconds;       // 刷新周期（秒）
    private double antiJam;              // 抗干扰能力（0-1）
    private double falseAlarmRate;       // 虚警率（0-1）
    private String description;
}
