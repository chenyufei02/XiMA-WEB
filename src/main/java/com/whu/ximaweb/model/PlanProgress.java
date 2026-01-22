package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 计划进度实体类 (适配 Navisworks 导出表结构)
 * 对应数据库表: plan_progress
 */
@Data
@TableName("plan_progress")
public class PlanProgress {

    @TableId(value = "P_Id", type = IdType.AUTO)
    private Integer pId;

    /**
     * 楼栋名称 (Navisworks 模型名)
     * 例如: "1", "12"
     */
    @TableField("Building")
    private String buildingName;

    @TableField("Floor")
    private String floor;

    @TableField("PlannedStart")
    private LocalDateTime plannedStart;

    @TableField("PlannedEnd")
    private LocalDateTime plannedEnd;

    @TableField("ActualStart")
    private LocalDateTime actualStart;

    @TableField("ActualEnd")
    private LocalDateTime actualEnd;
}