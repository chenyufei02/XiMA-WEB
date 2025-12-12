package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 楼栋实体类
 * 对应数据库中的 `sys_building` 表
 * 用于管理单体楼的名称、围栏坐标等信息
 */
@Data
@TableName("sys_building")
public class SysBuilding {

    /**
     * 楼栋ID (自增主键)
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属项目ID
     * (对应 sys_project 表的 id)
     */
    private Integer projectId;

    /**
     * 楼栋名称 (例如: "1号楼", "综合教学楼")
     */
    private String name;

    /**
     * 电子围栏坐标集合 (JSON字符串)
     * 格式示例: [{"lat":30.5, "lng":114.3}, {"lat":30.6, "lng":114.4}]
     */
    private String boundaryCoords;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}