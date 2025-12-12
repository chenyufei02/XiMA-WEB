package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.ProjectPhoto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ProjectPhoto 表的数据访问接口
 */
@Mapper
public interface ProjectPhotoMapper extends BaseMapper<ProjectPhoto> {

    /**
     * 自定义查询：根据项目ID获取该项目下所有的照片
     * (虽然 MyBatisPlus 自带 selectList，但显式写出来有助于后续扩展复杂查询)
     */
    @Select("SELECT * FROM project_photo WHERE project_id = #{projectId} ORDER BY shoot_time DESC")
    List<ProjectPhoto> selectByProjectId(@Param("projectId") Integer projectId);
}