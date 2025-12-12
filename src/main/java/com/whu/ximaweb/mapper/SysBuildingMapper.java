package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.SysBuilding;
import org.apache.ibatis.annotations.Mapper;

/**
 * SysBuilding 表的数据访问接口
 * 继承 BaseMapper 后自动拥有基础的 CRUD 能力
 */
@Mapper
public interface SysBuildingMapper extends BaseMapper<SysBuilding> {
}