package com.whu.ximaweb.service;

public interface ProgressService {
    void updateProgress(String key, int percent);
    Integer getProgress(String key);
    void removeProgress(String key);
    // 如果用了上面代码里的status功能，这里也要加上定义
    void updateStatus(String key, String status);
    String getStatus(String key);
    void calculateProjectProgress(Integer projectId);
}