package com.whu.ximaweb.service.impl;

import com.obs.services.ObsClient;
import com.obs.services.model.ListObjectsRequest;
import com.obs.services.model.ObjectListing;
import com.obs.services.model.ObsObject;
import com.whu.ximaweb.service.ObsService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObsServiceImpl implements ObsService {

    @Override
    public boolean validateConnection(String ak, String sk, String endpoint, String bucketName) {
        ObsClient obsClient = null;
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            return obsClient.headBucket(bucketName);
        } catch (Exception e) {
            System.err.println("OBS连接验证失败: " + e.getMessage());
            return false;
        } finally {
            closeClient(obsClient);
        }
    }

    @Override
    public boolean doesObjectExist(String ak, String sk, String endpoint, String bucketName, String objectKey) {
        ObsClient obsClient = null;
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            return obsClient.doesObjectExist(bucketName, objectKey);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            closeClient(obsClient);
        }
    }

    @Override
    public void uploadStream(String ak, String sk, String endpoint, String bucketName, String objectKey, InputStream stream) {
        ObsClient obsClient = null;
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            obsClient.putObject(bucketName, objectKey, stream);
        } catch (Exception e) {
            System.err.println("上传OBS失败: " + e.getMessage());
            throw new RuntimeException("OBS上传失败", e);
        } finally {
            closeClient(obsClient);
        }
    }

    /**
     * 实现修正后的列表获取逻辑：
     * 1. 使用 projectRoot (项目名) 作为 prefix 向 OBS 查询，获取该项目下所有文件。
     * 2. 在内存中检查 fileKey 是否包含 keyword (激光测距)。
     */
    @Override
    public List<String> listFiles(String ak, String sk, String endpoint, String bucketName, String projectRoot, String keyword) {
        ObsClient obsClient = null;
        List<String> fileKeys = new ArrayList<>();
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            ListObjectsRequest request = new ListObjectsRequest(bucketName);

            // 1. 设置搜索范围为当前项目目录 (例如 "西马路项目/")
            // 如果 projectRoot 为空，则扫描整个桶
            if (projectRoot != null && !projectRoot.isEmpty()) {
                // 确保以 / 结尾，避免匹配到类似 "西马路项目二期" 的前缀
                String prefix = projectRoot.endsWith("/") ? projectRoot : projectRoot + "/";
                request.setPrefix(prefix);
            }

            request.setMaxKeys(1000);

            ObjectListing result;
            do {
                result = obsClient.listObjects(request);
                for (ObsObject obsObject : result.getObjects()) {
                    String key = obsObject.getObjectKey();
                    // 过滤文件夹自身 (以/结尾的对象通常是文件夹占位符)
                    if (key.endsWith("/")) {
                        continue;
                    }

                    // 2. 核心过滤逻辑：路径中是否包含关键词
                    if (keyword != null && !keyword.isEmpty()) {
                        if (key.contains(keyword)) {
                            fileKeys.add(key);
                        }
                    } else {
                        // 没有关键词则全部加入
                        fileKeys.add(key);
                    }
                }
                request.setMarker(result.getNextMarker());
            } while (result.isTruncated());

        } catch (Exception e) {
            System.err.println("列举OBS文件失败: " + e.getMessage());
        } finally {
            closeClient(obsClient);
        }
        return fileKeys;
    }

    @Override
    public InputStream downloadFile(String ak, String sk, String endpoint, String bucketName, String objectKey) {
        ObsClient obsClient = null;
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            // 获取对象
            ObsObject obsObject = obsClient.getObject(bucketName, objectKey);
            if (obsObject != null && obsObject.getObjectContent() != null) {
                try (InputStream content = obsObject.getObjectContent()) {
                    // 内存转存：将流读入 byte数组，避免连接关闭后流不可用
                    return new ByteArrayInputStream(content.readAllBytes());
                }
            }
        } catch (Exception e) {
            System.err.println("下载OBS文件失败 [" + objectKey + "]: " + e.getMessage());
        } finally {
            closeClient(obsClient);
        }
        return null;
    }

    private void closeClient(ObsClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}