package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    /**
     * --- 核心修正 ---
     * 方法的返回类型现在是 ResponseEntity<ApiResponse<Object>>。
     * Object 表示这次响应我们不返回额外的数据 (data 字段为 null)。
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refreshProgress() {
        progressService.refreshActualProgress();

        // 使用我们创建的静态方法来快速构建一个成功的响应对象
        ApiResponse<Object> response = ApiResponse.success("Refresh task started successfully.");

        // 返回这个响应对象。Spring Boot 会自动把它转换成结构清晰的 JSON。
        return ResponseEntity.ok(response);
    }
}