package com.whu.ximaweb.service;

import java.io.InputStream;

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
}