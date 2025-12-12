package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 建筑楼层信息实体类
 * 对应数据库中的 `building_floor_info` 表
 * 记录每一栋楼的楼层高度标尺
 */
@Data
@TableName("building_floor_info")
public class BuildingFloorInfo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId; // 项目ID

    /**
     * 新增：所属楼栋ID
     * 楼层标尺必须具体到某栋楼
     */
    private Integer buildingId;

    private String projectName; // 冗余字段

    private Integer floorNumber; // 楼层号 (1, 2, 3...)

    private BigDecimal floorHeight; // 本层层高

    private BigDecimal cumulativeHeight; // 累计标高 (例如: 1层顶标高5.0m, 2层顶标高9.0m)
}