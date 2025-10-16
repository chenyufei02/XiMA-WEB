package com.whu.ximaweb.service;

import com.obs.services.model.ObsObject;

import java.io.InputStream;
import java.util.List;

/**
 * 华为云对象存储服务 (OBS) 的业务逻辑接口。
 * 定义了所有与云存储交互的操作。
 */
public interface ObsService {

    /**
     * 列出存储桶 (Bucket) 中的所有对象（文件）。
     * 这是我们将实现的第一个功能，用于验证与华为云的连接是否成功。
     * @return 一个包含所有对象信息的列表。
     */
    List<ObsObject> listObjects();

    // ... (之前的 listObjects() 方法) ...

    /**
     * 根据对象键（文件名）获取对象的输入流。
     * @param objectKey 文件的完整路径名
     * @return 包含文件内容的输入流
     */
    InputStream getObjectInputStream(String objectKey);

}