package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("actual_progress")
public class ActualProgress {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId;
    private String projectName;
    private Integer buildingId;
    private LocalDate measurementDate;

    // ... (保留你原有的 height, h1, h2, droneAlt 等字段) ...
    private BigDecimal actualHeight;
    private BigDecimal h1Val;
    private BigDecimal h2Val;
    private BigDecimal droneAlt;
    private Boolean isH2Measured;
    private Integer floorLevel;

    // 👇👇👇 【请新增这个字段】 👇👇👇
    /**
     * 参与当日计算的有效照片数量
     * (用于前端判断数据的可信度：<3张显示黄色预警)
     */
    private Integer photoCount;

    private LocalDateTime createdAt;
}