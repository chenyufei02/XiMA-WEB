package com.whu.ximaweb.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 * 负责生成和解析 Token
 */
@Component
public class JwtUtils {

    // 密钥，请勿泄露，实际开发中可以配置在 application.properties 中
    private static final String SECRET_KEY = "XiMaProject_SecretKey_For_BiShe_2025";

    // Token 有效期：24小时 (毫秒)
    private static final long EXPIRATION_TIME = 1000 * 60 * 60 * 24;

    /**
     * 生成 Token
     * @param userId 用户ID
     * @param username 用户名
     * @return 加密后的 Token 字符串
     */
    public String generateToken(Integer userId, String username) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username) // 设置主题（通常是用户名）
                .setIssuedAt(new Date(System.currentTimeMillis())) // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 过期时间
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY) // 签名算法和密钥
                .compact();
    }

    /**
     * 解析 Token 获取 Claims (载荷信息)
     * @param token 加密后的 Token
     * @return 包含用户信息的 Claims 对象
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            // 解析失败（过期或篡改）
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     * @param token
     * @return true=有效, false=无效
     */
    public boolean validateToken(String token) {
        return parseToken(token) != null;
    }

    /**
     * 从 Token 中获取 User ID
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims != null) {
            return (Integer) claims.get("userId");
        }
        return null;
    }
}