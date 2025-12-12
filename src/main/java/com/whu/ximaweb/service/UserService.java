package com.whu.ximaweb.service;

import com.whu.ximaweb.dto.LoginRequest;

/**
 * 用户业务服务接口
 */
public interface UserService {

    /**
     * 用户登录
     * @param request 登录请求参数
     * @return 登录成功后返回 JWT Token，失败则抛出异常
     */
    String login(LoginRequest request);
}