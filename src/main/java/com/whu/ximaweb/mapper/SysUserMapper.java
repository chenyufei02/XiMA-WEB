package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.SysUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper 接口
 * 继承 BaseMapper 后自动拥有基础 CRUD 能力
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}