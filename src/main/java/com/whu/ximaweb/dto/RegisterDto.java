package com.whu.ximaweb.dto;

import lombok.Data;

/**
 * 注册请求参数 DTO
 * 用于接收前端 /register 接口提交的数据
 */
@Data
public class RegisterDto {
    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 手机号 (可选)
     */
    private String phone;

    /**
     * 邮箱 (必填，用于验证)
     */
    private String email;

    /**
     * 邮箱验证码 (必填)
     */
    private String code;
}