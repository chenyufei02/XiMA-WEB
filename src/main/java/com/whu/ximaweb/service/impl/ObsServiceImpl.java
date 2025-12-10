//package com.whu.ximaweb.service.impl;
//
//import com.obs.services.ObsClient;
//import com.obs.services.model.ObjectListing;
//import com.obs.services.model.ObsObject;
//import com.whu.ximaweb.service.ObsService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import java.io.InputStream; // 导入 InputStream
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * ObsService 接口的实现类。
// */
//@Service
//public class ObsServiceImpl implements ObsService {
//
//    @Autowired
//    private ObsClient obsClient;
//
//    @Value("${huawei.obs.bucket-name}")
//    private String bucketName;
//
//    @Override
//    public List<ObsObject> listObjects() {
//        System.out.println("正在从华为云 OBS 列出对象... 桶名称: " + bucketName);
//        try {
//            ObjectListing result = obsClient.listObjects(bucketName);
//            return result.getObjects();
//        } catch (Exception e) {
//            System.err.println("访问华为云 OBS 失败！错误: " + e.getMessage());
//            e.printStackTrace();
//            return new ArrayList<>();
//        }
//    }
//
//    /**
//     * --- 新增方法 ---
//     * 获取单个对象输入流的具体实现。
//     */
//    @Override
//    public InputStream getObjectInputStream(String objectKey) {
//        try {
//            // OBS SDK 的标准用法：调用 getObject 方法，它会返回一个包含文件信息的 ObsObject。
//            // 它的 .getObjectContent() 方法就是我们需要的输入流。
//            ObsObject object = obsClient.getObject(bucketName, objectKey);
//            return object.getObjectContent();
//        } catch (Exception e) {
//            System.err.println("获取文件 '" + objectKey + "' 的输入流时失败: " + e.getMessage());
//            return null; // 失败时返回 null
//        }
//    }
//}