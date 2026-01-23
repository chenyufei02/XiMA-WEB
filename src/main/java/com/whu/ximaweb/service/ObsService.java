package com.whu.ximaweb.service;

import java.io.InputStream;
import java.util.List;

/**
 * 华为云 OBS 服务接口 (SaaS动态版)
 * 所有操作都需要传入 AK/SK/Bucket，不再依赖全局配置
 */
public interface ObsService {

    /**
     * 验证 OBS 连接配置是否正确 (用于项目导入时的校验)
     */
    boolean validateConnection(String ak, String sk, String endpoint, String bucketName);

    /**
     * 判断文件是否已存在 (避免重复上传)
     */
    boolean doesObjectExist(String ak, String sk, String endpoint, String bucketName, String objectKey);

    /**
     * 上传文件流到 OBS
     */
    void uploadStream(String ak, String sk, String endpoint, String bucketName, String objectKey, InputStream stream);

/**
     * 修正：列举并过滤文件
     * @param projectRoot 项目根目录前缀 (如 "西马路项目/")，用于缩小扫描范围
     * @param keyword 文件夹关键词 (如 "激光测距")，用于模糊匹配过滤，若为空则返回所有
     * @return 符合条件的文件Key列表
     */
    List<String> listFiles(String ak, String sk, String endpoint, String bucketName, String projectRoot, String keyword);
    /**
     * 新增：下载文件流 (用于后续解析XMP)
     */
    InputStream downloadFile(String ak, String sk, String endpoint, String bucketName, String objectKey);


}