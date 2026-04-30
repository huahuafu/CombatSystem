package com.military.combat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Find 环节标准化输出：目标接触报告。
 */
@Data
@Document(collection = "find_contact_reports")
public class FindContactReport {
    @Id
    private String id;

    private String scenarioId;
    private int round;
    private long timestamp;

    // 标准输出字段
    private String contactId;          // 报告编号
    private String targetUnitId;       // 若为虚警则为 null
    private String targetName;
    private String targetType;
    private Double latitude;
    private Double longitude;
    private Double x;
    private Double y;
    private double speed;              // m/s
    private double heading;            // 0~359
    private String featureSignature;   // 特征摘要
    private double confidence;         // 0~1

    // 识别字段
    private String friendFoe;          // FRIEND/FOE/NEUTRAL/UNKNOWN
    private String militaryOrCivilian; // MILITARY/CIVILIAN/UNKNOWN
    private String mobility;           // MOBILE/STATIC/UNKNOWN
    private String camouflageState;    // REAL/CAMOUFLAGED/DECOY/UNKNOWN
    private String threatLevel;        // HIGH/MEDIUM/LOW
    private String combatIntent;       // ATTACK/DEFEND/RECON/SUPPORT/UNKNOWN
    private int valueScore;            // 0~100

    // 探测链路
    private List<String> sensorSources = new ArrayList<>();
    private boolean falseAlarm;
    private boolean identified;
}
