package com.whu.ximaweb.model;

import java.time.LocalDate;

/**
 * 照片数据模型类
 */
public class PhotoData {

    public enum MeasurementType {
        H1_ROOF,
        H2_GROUND,
        UNKNOWN
    }

    private final String fileName;
    private final LocalDate captureDate;
    private final MeasurementType type;
    private final double distance;
    private final double absoluteAltitude;
    private final double latitude;  // 纬度
    private final double longitude; // 经度

    public PhotoData(String fileName, LocalDate captureDate, MeasurementType type, double distance,
                     double absoluteAltitude, double latitude, double longitude) {
        this.fileName = fileName;
        this.captureDate = captureDate;
        this.type = type;
        this.distance = distance;
        this.absoluteAltitude = absoluteAltitude;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    // --- Getter 方法 (补全缺失的部分) ---

    public String getFileName() { return fileName; }
    public LocalDate getCaptureDate() { return captureDate; }
    public MeasurementType getType() { return type; }
    public double getDistance() { return distance; }

    // 补上这三个缺失的方法：
    public double getAbsoluteAltitude() { return absoluteAltitude; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public String toString() {
        return String.format(
            "照片: %s [日期: %s, 类型: %s, 距离: %.2f米]",
            fileName, captureDate, type, distance
        );
    }
}