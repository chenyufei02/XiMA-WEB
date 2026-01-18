package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工程照片实体类
 * 对应数据库表: project_photo
 * 记录照片的存储路径、元数据及所属项目
 */
@Data
@TableName("project_photo")
public class ProjectPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属项目ID
     * 用于实现多租户数据隔离，确保用户只能看到自己项目的照片
     */
    private Integer projectId;

    /**
     * 照片在对象存储(OBS)或本地的访问URL
     */
    private String photoUrl;

    /**
     * 原始XMP数据备份 (Text类型)
     */
    private String xmpMetadata;

    /**
     * 激光测距值 (对应 h1)
     */
    private BigDecimal laserDistance;

    /**
     * 无人机绝对飞行高度 (米)
     * 用于H2智能推算: H2_new = H2_old + (Alt_new - Alt_old)
     */
    private BigDecimal absoluteAltitude;

    /**
     * 拍摄点纬度
     */
    private BigDecimal gpsLat;

    /**
     * 拍摄点经度
     */
    private BigDecimal gpsLng;

    /**
     * 拍摄时间
     */
    private LocalDateTime shootTime;
}