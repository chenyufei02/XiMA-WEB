package com.whu.ximaweb.model;

import java.time.LocalDateTime;

/**
 * 照片数据模型类 (升级版：支持精确时间)
 */
public class PhotoData {

    public enum MeasurementType {
        H1_ROOF,
        H2_GROUND,
        UNKNOWN
    }

    private final String fileName;
    // ✅ 修改：从 LocalDate 改为 LocalDateTime
    private final LocalDateTime captureTime;
    private final MeasurementType type;
    private final double distance;
    private final double absoluteAltitude;
    private final double latitude;
    private final double longitude;

    public PhotoData(String fileName, LocalDateTime captureTime, MeasurementType type, double distance,
                     double absoluteAltitude, double latitude, double longitude) {
        this.fileName = fileName;
        this.captureTime = captureTime;
        this.type = type;
        this.distance = distance;
        this.absoluteAltitude = absoluteAltitude;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getFileName() { return fileName; }
    // ✅ 修改：Getter 返回 LocalDateTime
    public LocalDateTime getCaptureTime() { return captureTime; }
    public MeasurementType getType() { return type; }
    public double getDistance() { return distance; }
    public double getAbsoluteAltitude() { return absoluteAltitude; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    @Override
    public String toString() {
        return String.format(
            "照片: %s [时间: %s, 距离: %.2f米]",
            fileName, captureTime, distance
        );
    }
}