package com.whu.ximaweb.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 照片元数据传输对象 (DTO)
 * 用于在 PhotoProcessor 解析后临时存储数据，随后传递给 Task 进行入库
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PhotoData {

    /**
     * 照片文件名
     */
    private String fileName;

    /**
     * 拍摄时间
     */
    private LocalDateTime captureTime;

    /**
     * 测量类型 (保留字段，暂设为 UNKNOWN)
     */
    private MeasurementType type;

    /**
     * 激光测距距离 (对应数据库的 laser_distance / h1)
     * 来源 XMP: drone-dji:LRFTargetDistance
     */
    private double distance;

    /**
     * 目标点绝对高度 (注意：这是激光打到的点的海拔)
     * 来源 XMP: drone-dji:LRFTargetAbsAlt
     */
    private double targetAbsAltitude;

    /**
     * ✅ 新增字段：无人机绝对飞行高度
     * 来源 XMP: drone-dji:AbsoluteAltitude
     * 用途：用于 H2 的智能推算算法
     */
    private double droneAbsoluteAltitude;

    /**
     * 拍摄点纬度
     */
    private double latitude;

    /**
     * 拍摄点经度
     */
    private double longitude;

    /**
     * 测量类型枚举
     */
    public enum MeasurementType {
        UNKNOWN,
        H1, // 楼顶
        H2  // 地面
    }
}