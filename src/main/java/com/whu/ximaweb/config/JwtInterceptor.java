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
 * JWT 拦截器
 * 作用：拦截请求，检查 Token 有效性
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取请求头中的 Token
        // 前端通常约定 Header 为： Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");

        // 2. 如果是 OPTIONS 请求（跨域预检），直接放行
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        // 3. 校验 Token 格式
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            // 去掉 "Bearer " 前缀拿到真正的 token 字符串
            String token = authHeader.substring(7);

            // 4. 验证 Token 是否有效
            Claims claims = jwtUtils.parseToken(token);
            if (claims != null) {
                // Token 有效！
                // 关键步骤：把 Token 里存的用户ID取出来，放到 Request 属性里
                // 这样 Controller 就能知道当前是谁在操作了
                Integer userId = (Integer) claims.get("userId");
                request.setAttribute("currentUser", userId);
                return true; // 放行
            }
        }

        // 5. 验证失败，拦截请求并返回 401 错误 JSON
        response.setStatus(401);
        response.setContentType("application/json;charset=utf-8");
        PrintWriter writer = response.getWriter();
        // 手动构造一个 JSON 返回给前端
        String jsonResponse = new ObjectMapper().writeValueAsString(ApiResponse.error(401, "未登录或 Token 已过期，请重新登录"));
        writer.print(jsonResponse);
        writer.flush();
        writer.close();

        return false; // 拦截
    }
}