package com.whu.ximaweb.dto;

import lombok.Data;

/**
 * 项目导入请求参数
 */
@Data
public class ProjectImportRequest {
    // 基础信息
    private String projectName;
    private String photoFolderKeyword; // 文件夹关键词

    // 大疆配置
    private String djiOrgKey;
    private String djiProjectUuid;

    // 华为云配置
    private String obsBucketName;
    private String obsAk;
    private String obsSk;
    private String obsEndpoint;
}