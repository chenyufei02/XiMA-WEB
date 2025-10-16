package com.whu.ximaweb.model;

import java.time.LocalDate;

/**
 * 照片数据模型类 (一个数据 "收纳盒")
 * 这个类的作用是，把从一张照片元数据中解析出来的、我们关心的所有信息，
 * 有条理地存储在一个 Java 对象中。
 */
public class PhotoData {

    /**
     * 枚举类型，用来清晰地表示这张照片是测量楼顶(H1)还是地面(H2)。
     */
    public enum MeasurementType {
        H1_ROOF, // 测量楼顶
        H2_GROUND, // 测量地面
        UNKNOWN  // 未知或无法判断
    }

    private final String fileName; // 照片的文件名
    private final LocalDate captureDate; // 拍摄日期
    private final MeasurementType type; // 测量的类型 (H1 还是 H2)
    private final double distance; // 激光测距的距离
    private final double absoluteAltitude; // 目标的绝对高度
    private final double latitude; // 目标的纬度
    private final double longitude; // 目标的经度

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

    // --- Getter 方法 ---

    public LocalDate getCaptureDate() {
        return captureDate;
    }

    public MeasurementType getType() {
        return type;
    }

    public double getDistance() {
        return distance;
    }

    @Override
    public String toString() {
        return String.format(
            "照片: %s [日期: %s, 类型: %s, 距离: %.2f米]",
            fileName, captureDate, type, distance
        );
    }
}