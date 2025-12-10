package com.whu.ximaweb.service;

import com.whu.ximaweb.dto.dji.DjiAnnotationDto;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import java.util.List;

/**
 * 负责与大疆司空2 OpenAPI 交互的服务接口
 * 所有方法均需要传入鉴权信息，支持多用户动态调用
 */
public interface DjiService {

    /**
     * 根据提供的 API Key 获取该账号下的所有项目列表
     * (用于用户导入项目时的向导)
     * @param apiKey 大疆组织密钥
     * @return 项目列表
     */
    List<DjiProjectDto> getProjects(String apiKey);

    /**
     * 获取指定项目下的所有面状标注（电子围栏）
     * @param projectUuid 项目UUID
     * @param apiKey 大疆组织密钥
     * @return 标注列表
     */
    List<DjiAnnotationDto> getPolygonAnnotations(String projectUuid, String apiKey);

    /**
     * 获取指定项目下、指定文件夹名称关键词的照片列表
     * @param projectUuid 项目UUID
     * @param apiKey 大疆组织密钥
     * @param folderNameKeyword 文件夹名称关键词（如"请勿删"）
     * @return 照片文件列表
     */
    List<DjiMediaFileDto> getPhotosFromFolder(String projectUuid, String apiKey, String folderNameKeyword);
}