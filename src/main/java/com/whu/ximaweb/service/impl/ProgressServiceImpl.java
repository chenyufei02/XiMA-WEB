package com.whu.ximaweb.service.impl;

import com.whu.ximaweb.service.ProgressService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进度管理服务实现类
 * 用于记录异步任务（如文件上传、照片处理）的进度
 */
@Service
public class ProgressServiceImpl implements ProgressService {

    // 使用线程安全的 Map 来存储进度：Key是任务ID(uploadId), Value是进度百分比(0-100)
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    // 存储任务状态描述（可选），例如 "正在解析XMP..."
    private final Map<String, String> statusMap = new ConcurrentHashMap<>();

    @Override
    public void updateProgress(String key, int percent) {
        progressMap.put(key, percent);
    }

    @Override
    public Integer getProgress(String key) {
        // 如果找不到key，默认返回0
        return progressMap.getOrDefault(key, 0);
    }

    @Override
    public void removeProgress(String key) {
        progressMap.remove(key);
        statusMap.remove(key);
    }

    // 以下是可选方法，如果你的接口定义里有 updateStatus 就加上
    @Override
    public void updateStatus(String key, String status) {
        statusMap.put(key, status);
    }

    @Override
    public String getStatus(String key) {
        return statusMap.getOrDefault(key, "");
    }
}