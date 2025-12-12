package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.dto.LoginRequest;
import com.whu.ximaweb.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证相关的控制器
 * 处理登录、注册等请求
 */
@RestController
@RequestMapping("/api/auth")
public class LoginController {

    @Autowired
    private UserService userService;

    /**
     * 登录接口
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@RequestBody LoginRequest request) {
        try {
            // 调用 Service 执行登录逻辑
            String token = userService.login(request);

            // 封装返回数据
            Map<String, String> data = new HashMap<>();
            data.put("token", token);

            return ApiResponse.success("登录成功", data);

        } catch (Exception e) {
            return ApiResponse.error(401, "登录失败: " + e.getMessage());
        }
    }
}