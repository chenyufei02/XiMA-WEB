package com.whu.ximaweb.service.impl;

import com.whu.ximaweb.service.DjiService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * DjiService 接口的实现类。
 */
@Service
public class DjiServiceImpl implements DjiService {

    // 大疆司空2公有云 OpenAPI 的根地址
    private static final String DJI_API_BASE_URL = "https://fh.dji.com/api/v1";

    // 注入由 Spring 管理的 OkHttpClient 实例
    @Autowired
    private OkHttpClient httpClient;

    // 从 application.properties 文件中读取配置好的组织密钥
    @Value("${dji.api.organization-key}")
    private String organizationKey;

    @Override
    public String getProjects() {
        // 构建请求URL，用于获取项目列表
        String url = DJI_API_BASE_URL + "/projects";

        // 创建一个HTTP GET请求
        Request request = new Request.Builder()
                .url(url)
                // 添加API要求的认证头信息，这是关键
                .addHeader("X-Organization-Key", organizationKey)
                .build();

        // 使用 OkHttpClient 发送请求并获取响应
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                // 如果请求成功，返回响应的JSON字符串
                return response.body().string();
            } else {
                // 如果请求失败，打印错误信息
                System.err.println("调用大疆API失败: " + response.code() + " " + response.message());
                return null;
            }
        } catch (IOException e) {
            System.err.println("调用大疆API时发生网络异常: " + e.getMessage());
            return null;
        }
    }
}