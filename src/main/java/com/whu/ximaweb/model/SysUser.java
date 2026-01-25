package com.whu.ximaweb.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统用户实体类
 * 对应数据库 sys_user 表
 * ✅ 已更新：补充邮箱和手机号字段
 */
@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private String username;
    private String password;
    private String realName;

    /**
     * 手机号 (用于接收紧急通知)
     */
    private String phone;

    /**
     * 邮箱 (用于接收验证码和系统报警)
     */
    private String email;

    private LocalDateTime createdAt;

    /**
     * 每日AI报告发送时间 (格式 HH:mm，如 "09:00")
     */
    private String reportTime;
}