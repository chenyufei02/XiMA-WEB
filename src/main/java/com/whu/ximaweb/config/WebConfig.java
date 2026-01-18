package com.whu.ximaweb.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置类
 * 用于注册拦截器
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 JWT 拦截器
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**") // 拦截所有 /api 开头的接口
                .excludePathPatterns(
                        "/api/auth/login",    // 排除登录接口
                        "/api/auth/register", // 排除注册接口
                        "/api/auth/send-code", // 排除发送验证码接口
                        "/api/projects/dji-workspaces", // 保持开放：允许查询大疆项目（暂时无需登录校验）
                        "/api/hello"          // 排除测试接口
                );
    }
}