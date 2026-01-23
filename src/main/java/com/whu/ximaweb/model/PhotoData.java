package com.whu.ximaweb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 照片元数据传输对象 (DTO)
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoData {

    private String fileName;
    private LocalDateTime captureTime;
    private MeasurementType type;
    private double distance;           // 激光测距 H1
    private double targetAbsAltitude;  // 目标点海拔
    private double droneAbsoluteAltitude; // 飞机海拔

    /** 飞机自身坐标 */
    private double latitude;
    private double longitude;

    /** ✅ 激光测距目标点坐标 */
    private double lrfTargetLat;
    private double lrfTargetLng;

    public enum MeasurementType {
        UNKNOWN, H1, H2
    }
}