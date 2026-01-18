package com.whu.ximaweb.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.util.JwtUtils;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * JWT 拦截器 (调试版)
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求 URL，方便定位
        String requestURI = request.getRequestURI();

        // 放行 OPTIONS 请求
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 2. 获取 Header
        String authHeader = request.getHeader("Authorization");
        System.out.println("========== JWT 拦截器日志 ==========");
        System.out.println("请求接口: " + requestURI);
        System.out.println("Authorization 头信息: " + authHeader);

        // 3. 校验格式
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // 去掉 "Bearer "
            try {
                // 4. 解析 Token
                Claims claims = jwtUtils.parseToken(token);
                if (claims != null) {
                    Integer userId = (Integer) claims.get("userId");
                    String username = (String) claims.get("username");

                    System.out.println("✅ Token 验证成功! 用户ID: " + userId + ", 用户名: " + username);

                    // 放入 Request 供 Controller 使用
                    request.setAttribute("currentUser", userId);
                    return true;
                } else {
                    System.err.println("❌ Token 解析结果为 null (可能已过期或签名不匹配)");
                }
            } catch (Exception e) {
                System.err.println("❌ Token 解析发生异常: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("❌ Header 格式错误或为空");
        }
        System.out.println("==================================");

        // 5. 验证失败
        response.setStatus(401);
        response.setContentType("application/json;charset=utf-8");
        PrintWriter writer = response.getWriter();
        String jsonResponse = new ObjectMapper().writeValueAsString(ApiResponse.error(401, "未登录或 Token 无效(后端拦截)"));
        writer.print(jsonResponse);
        writer.flush();
        writer.close();

        return false;
    }
}