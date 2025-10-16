package com.whu.ximaweb.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 通用的API响应数据结构。
 * 这个类定义了所有后端接口返回给前端的JSON数据的标准格式。
 * @param <T> T 代表 "Type"，它是一个泛型，表示响应数据可以是任何类型。
 */
@Data // Lombok 注解，自动生成 getter, setter 等方法
public class ApiResponse<T> {

    /**
     * 状态码 (例如: 200 代表成功, 500 代表服务器错误)
     */
    private Integer status;

    /**
     * 响应消息 (例如: "操作成功")
     */
    private String message;

    /**
     * 实际的响应数据。
     * 它可以是任何东西，比如一个列表、一个单一的对象等。
     * 对于不需要返回额外数据的操作（比如我们的刷新操作），这个字段可以为 null。
     */
    @Schema(nullable = true)
    private T data;

    /**
     * 一个静态工厂方法，用于快速创建一个表示成功的响应对象。
     * @param message 成功消息
     * @param data 响应数据
     * @return 一个配置好的 ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setStatus(200);
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    /**
     * 一个重载的 success 方法，用于不需要返回额外数据的成功响应。
     * @param message 成功消息
     * @return 一个配置好的 ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(String message) {
        return success(message, null);
    }
}