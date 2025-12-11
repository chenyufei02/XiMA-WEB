package com.whu.ximaweb.service;

import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import java.util.List;

/**
 * 负责与大疆司空2 OpenAPI 交互的服务接口
 */
public interface DjiService {

    /**
     * 方法1：使用默认配置获取原始 JSON (适配 ProgressController)
     */
    String getProjects();

    /**
     * 方法2：使用指定 Key 获取解析后的列表 (适配 ProjectController)
     */
    List<DjiProjectDto> getProjects(String apiKey);

    /**
     * 获取指定项目下的媒体文件列表 (适配 ProjectController)
     */
    List<DjiMediaFileDto> getPhotosFromFolder(String projectUuid, String apiKey, String folderNameKeyword);
}