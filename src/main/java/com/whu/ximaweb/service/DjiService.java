package com.whu.ximaweb.service;

/**
 * 负责与大疆司空2 OpenAPI 交互的服务接口。
 */
public interface DjiService {

    /**
     * 获取指定组织下的所有项目列表。
     * 这是一个基础的API调用，用于测试与司空2的连接和认证是否成功。
     * @return 从API返回的原始JSON字符串。
     */
    String getProjects();

    // 后续我们将在这里添加获取地图标注的方法
    // String getAnnotation(String projectId, String annotationName);
}