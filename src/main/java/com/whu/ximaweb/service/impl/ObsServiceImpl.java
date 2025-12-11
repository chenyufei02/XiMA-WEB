package com.whu.ximaweb.service.impl;

import com.obs.services.ObsClient;
import com.whu.ximaweb.service.ObsService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
public class ObsServiceImpl implements ObsService {

    @Override
    public boolean validateConnection(String ak, String sk, String endpoint, String bucketName) {
        ObsClient obsClient = null;
        try {
            obsClient = new ObsClient(ak, sk, endpoint);
            // 尝试获取桶的元数据，如果账号或桶名不对，这里会抛异常
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
            System.out.println("成功上传文件至OBS: " + objectKey);
        } catch (Exception e) {
            System.err.println("上传OBS失败: " + e.getMessage());
            throw new RuntimeException("OBS上传失败", e);
        } finally {
            closeClient(obsClient);
        }
    }

    private void closeClient(ObsClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                // 忽略关闭时的异常
            }
        }
    }
}