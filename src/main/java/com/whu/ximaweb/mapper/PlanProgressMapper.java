package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.PlanProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlanProgressMapper extends BaseMapper<PlanProgress> {

    // ✅ 核心查询：查找某项目下，所有【未被绑定】的 Navisworks 楼名
    // 逻辑：从 plan_progress 查出所有 distinct Building，排除掉 sys_building 里已经用过的 plan_building_name
    @Select("SELECT DISTINCT Building FROM plan_progress " +
            "WHERE Building NOT IN (SELECT IFNULL(plan_building_name, '') FROM sys_building WHERE project_id = #{projectId})")
    List<String> selectUnboundBuildingNames(Integer projectId);
}