package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 楼栋实体类
 * 对应数据库表: sys_building
 */
@Data
@TableName("sys_building")
public class SysBuilding {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer projectId;

    /**
     * 楼栋显示名称 (用户自定义，例如: "综合教学楼")
     */
    private String name;

    /**
     * ✅ 新增：绑定的计划进度楼名 (Navisworks中的名字，例如: "12")
     * 用于关联 plan_progress 表
     */
    @TableField("plan_building_name")
    private String planBuildingName;

    /**
     * 电子围栏坐标集合 (保持原样，电子围栏功能的核心字段)
     */
    private String boundaryCoords;

    private LocalDateTime createdAt;
}