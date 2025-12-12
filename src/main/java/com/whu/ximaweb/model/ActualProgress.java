package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 实际进度实体类
 * 记录每一栋楼、每一天的施工进度计算结果及原始参数
 */
@Data
@TableName("actual_progress")
public class ActualProgress {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId; // 项目ID

    /**
     * 新增：所属楼栋ID
     * 进度数据现在精确到"栋"
     */
    private Integer buildingId;

    private String projectName; // 冗余字段，保留以便快速查看

    private LocalDate measurementDate;

    // --- 计算结果 ---

    /**
     * 当天计算出的实际建筑高度 (h2 - h1)
     */
    private BigDecimal actualHeight;

    /**
     * 根据高度换算出的楼层数
     */
    private Integer floorLevel;

    // --- 核心算法参数 (新增) ---

    /**
     * 当天测量的楼顶距离 (h1, 平均值)
     */
    private BigDecimal h1Val;

    /**
     * 当天使用的地面基准距离 (h2)
     * 注意：这可能是实测值，也可能是基于历史推算的
     */
    private BigDecimal h2Val;

    /**
     * h2是否为实测?
     * true(1)=实测, false(0)=推算
     */
    private Boolean isH2Measured;

    /**
     * 拍摄时的无人机绝对高度
     * 如果是实测h2，此值必填，作为下一次推算的基准(Ref_Alt)
     */
    private BigDecimal droneAlt;

    private LocalDateTime createdAt;
}