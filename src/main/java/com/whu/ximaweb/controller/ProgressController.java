package com.whu.ximaweb.controller;

import com.whu.ximaweb.dto.ApiResponse;
import com.whu.ximaweb.service.DjiService; // 导入 DjiService
import com.whu.ximaweb.service.ProgressService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping; // 导入 GetMapping
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
public class ProgressController {

    @Autowired
    private ProgressService progressService;

    /**
     * --- 新增部分 ---
     * 依赖注入我们新创建的 DjiService。
     */
    @Autowired
    private DjiService djiService;

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Object>> refreshProgress() {
        progressService.refreshActualProgress();
        ApiResponse<Object> response = ApiResponse.success("Refresh task started successfully.");
        return ResponseEntity.ok(response);
    }

    /**
     * --- 新增部分 ---
     * 创建一个 GET 类型的测试接口，用于验证与大疆司空2 API的连接。
     * 完整访问路径为: GET http://localhost:8080/api/progress/dji-projects
     *
     * @return 返回从大疆API获取到的项目列表JSON字符串。
     */
    @GetMapping("/dji-projects")
    public ResponseEntity<ApiResponse<String>> getDjiProjects() {
        // 调用 djiService 的 getProjects 方法
        String projectsJson = djiService.getProjects();

        if (projectsJson != null) {
            // 如果成功获取到数据，就把它包装在 ApiResponse 中返回
            return ResponseEntity.ok(ApiResponse.success("Successfully fetched projects from DJI API.", projectsJson));
        } else {
            // 如果获取失败，返回一个错误信息
            ApiResponse<String> errorResponse = new ApiResponse<>();
            errorResponse.setStatus(500);
            errorResponse.setMessage("Failed to fetch projects from DJI API.");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}