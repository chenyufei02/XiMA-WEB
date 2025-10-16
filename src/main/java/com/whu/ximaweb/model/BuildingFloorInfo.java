package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 建筑楼层信息实体类
 * 这个类对应数据库中的 `building_floor_info` 表。
 */
@Data
@TableName("building_floor_info")
public class BuildingFloorInfo {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String projectName;

    private Integer floorNumber;

    private BigDecimal floorHeight;

    private BigDecimal cumulativeHeight;
}