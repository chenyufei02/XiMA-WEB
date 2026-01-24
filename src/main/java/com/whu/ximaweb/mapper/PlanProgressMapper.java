package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.PlanProgress;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PlanProgressMapper extends BaseMapper<PlanProgress> {

    // 核心查询：查找某项目下，所有【未被绑定】的 Navisworks 楼名
    @Select("SELECT DISTINCT Building FROM plan_progress " +
            "WHERE Building NOT IN (SELECT IFNULL(name, '') FROM sys_building WHERE project_id = #{projectId})")
    List<String> selectUnboundBuildingNames(Integer projectId);

    // ✅ 新增：根据楼栋模型名称删除该楼栋的所有旧计划（用于覆盖保存）
    @Delete("DELETE FROM plan_progress WHERE Building = #{buildingName}")
    int deleteByBuildingName(String buildingName);
}