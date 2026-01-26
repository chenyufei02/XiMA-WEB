package com.whu.ximaweb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.ximaweb.model.SysTaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 自动化任务日志 Mapper 接口
 * 基于 MyBatis-Plus，无 XML 配置
 */
@Mapper
public interface SysTaskLogMapper extends BaseMapper<SysTaskLog> {

    // 注意：BaseMapper 已经自动提供了 insert、selectById 等基础方法，无需手动编写

    /**
     * 面板专用：获取某项目最近的日志（用于滚动展示）
     * SQL 逻辑：查询该项目所有日志，按时间倒序，取前 limit 条
     */
    @Select("SELECT * FROM sys_task_log WHERE project_id = #{projectId} ORDER BY create_time DESC LIMIT #{limit}")
    List<SysTaskLog> selectRecentLogs(@Param("projectId") Integer projectId, @Param("limit") int limit);

    /**
     * 面板专用：统计累计执行次数（用于展示"累计发送XX次"）
     * SQL 逻辑：统计该项目下、指定类型、且状态为成功(1)的记录总数
     */
    @Select("SELECT COUNT(*) FROM sys_task_log WHERE project_id = #{projectId} AND task_type = #{taskType} AND status = 1")
    int countByProjectAndType(@Param("projectId") Integer projectId, @Param("taskType") String taskType);

    /**
     * 面板专用：查询最后一次成功的时间（用于展示"上次同步：10分钟前"）
     * SQL 逻辑：查询该项目下、指定类型、且状态为成功(1)的记录，按时间倒序取第1条的时间
     */
    @Select("SELECT create_time FROM sys_task_log WHERE project_id = #{projectId} AND task_type = #{taskType} AND status = 1 ORDER BY create_time DESC LIMIT 1")
    Date selectLatestTime(@Param("projectId") Integer projectId, @Param("taskType") String taskType);
}