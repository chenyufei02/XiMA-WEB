package com.whu.ximaweb.model;

import lombok.Data;
import java.util.Date;

/**
 * 自动化任务日志实体
 * 对应数据库表: sys_task_log
 */
@Data
public class SysTaskLog {
    private Integer id;

    /** 项目ID，核心字段，用于多用户权限隔离 */
    private Integer projectId;

    /** 任务类型: PHOTO_SYNC 或 DAILY_REPORT */
    private String taskType;

    /** 1=成功, 0=失败 */
    private Integer status;

    /** 面板上显示的简短描述 */
    private String message;

    private Date createTime;

    // 常量定义，防止手写字符串出错
    public static final String TYPE_PHOTO_SYNC = "PHOTO_SYNC";
    public static final String TYPE_DAILY_REPORT = "DAILY_REPORT";
}