package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.BuildingFloorInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * BuildingFloorInfo 表的数据访问接口 (Mapper)
 */
@Mapper
public interface BuildingFloorInfoMapper extends BaseMapper<BuildingFloorInfo> {
    // 同样地，继承 BaseMapper<BuildingFloorInfo> 后，
    // 这个接口就自动拥有了对 BuildingFloorInfo 实体类（也就是 building_floor_info 表）的常用增删改查能力。
}