package com.whu.ximaweb.dto;

import lombok.Data;

/**
 * 前后端交互的统一数据格式封装
 */
@Data
public class ApiResponse<T> {

    private int status;
    private String message;
    private T data;

    // --- 成功响应 (保持您原有的风格) ---

    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(200);
        response.setMessage(message);
        return response;
    }

    // --- 失败响应 (严格按照您的 Setter 风格补充) ---

    /**
     * 默认失败 (500)
     */
    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        // 默认错误码
        response.setStatus(500);
        response.setMessage(message);
        return response;
    }

    /**
     * 自定义错误码失败
     */
    public static <T> ApiResponse<T> error(int status, String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(status);
        response.setMessage(message);
        return response;
    }
}