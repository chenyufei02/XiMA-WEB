package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.ActualProgress;
import org.apache.ibatis.annotations.Mapper;

/**
 * ActualProgress 表的数据访问接口 (Mapper)
 */
@Mapper // @Mapper 注解告诉 Spring Boot，这是一个 Mybatis 的 Mapper 接口，需要被扫描和注入。
public interface ActualProgressMapper extends BaseMapper<ActualProgress> {
    // 继承了 BaseMapper<ActualProgress> 之后，这个接口就自动拥有了对 ActualProgress 实体类（也就是 actual_progress 表）的常用增删改查能力。
    // 目前我们不需要在这里添加任何自定义的方法。
}