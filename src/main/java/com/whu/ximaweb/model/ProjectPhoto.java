package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("project_photo")
public class ProjectPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer projectId;
    private String photoUrl;
    private String xmpMetadata;

    private BigDecimal laserDistance;
    private BigDecimal absoluteAltitude;
    private LocalDateTime shootTime;

    /** 飞机自身坐标 */
    private BigDecimal gpsLat;
    private BigDecimal gpsLng;

    /** ✅ 激光测距目标点坐标 */
    @TableField("lrf_target_lat")
    private BigDecimal lrfTargetLat;

    @TableField("lrf_target_lng")
    private BigDecimal lrfTargetLng;

    @TableField("is_marker")
    private Boolean isMarker;
}