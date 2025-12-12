package com.whu.ximaweb.dto;

import lombok.Data;

/**
 * 登录请求参数 DTO
 * 用于接收前端提交的用户名和密码
 */
@Data
public class LoginRequest {
    private String username;
    private String password;
}