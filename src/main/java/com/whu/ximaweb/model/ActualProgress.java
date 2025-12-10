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
 * 这个类对应数据库中的 `actual_progress` 表。
 */
@Data // @Data 是 Lombok 提供的注解，它会自动为我们生成 getter, setter, toString, equals, hashCode 等方法，让代码非常简洁。
@TableName("actual_progress") // @TableName 注解告诉 Mybatis-Plus，这个类对应的是数据库中的 "actual_progress" 表。
public class ActualProgress {

    /**
     * @TableId 注解表明这个属性是表的主键。
     * type = IdType.AUTO 表示主键是自增的。
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String projectName;

    private LocalDate measurementDate;

    // 对于金额、高度等需要精确计算的数字，我们使用 BigDecimal 类型，而不是 double，可以避免精度丢失。
    private BigDecimal actualHeight;

    private Integer floorLevel;

    private LocalDateTime createdAt;

    private Integer projectId;
}