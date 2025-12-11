package com.whu.ximaweb.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.service.DjiService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DjiServiceImpl implements DjiService {

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${dji.api.organization-key}")
    private String defaultOrganizationKey;

    @Value("${dji.api.base-url}")
    private String djiApiBaseUrl;

    /**
     * 实现方法1：无参版本
     * 直接使用默认配置的 Key 获取原始 JSON
     */
    @Override
    public String getProjects() {
        return fetchProjectsRaw(this.defaultOrganizationKey);
    }

    /**
     * 实现方法2：有参版本
     * 使用传入的 Key 获取并解析为 List 对象
     */
    @Override
    public List<DjiProjectDto> getProjects(String apiKey) {
        String json = fetchProjectsRaw(apiKey);
        if (json == null) {
            return new ArrayList<>();
        }

        try {
            JsonNode root = objectMapper.readTree(json);
            // 检查业务状态码 (code: 0 表示成功)
            if (root.has("code") && root.get("code").asInt() != 0) {
                System.err.println("大疆API业务报错: " + root.path("message").asText());
                return new ArrayList<>();
            }
            // 解析 data.list 节点
            JsonNode listNode = root.path("data").path("list");
            if (listNode.isArray()) {
                return objectMapper.convertValue(listNode, new TypeReference<List<DjiProjectDto>>() {});
            }
        } catch (Exception e) {
            System.err.println("JSON解析失败: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * 实现方法3：获取媒体文件列表
     * 对应接口定义中的 getPhotosFromFolder
     */
    @Override
    public List<DjiMediaFileDto> getPhotosFromFolder(String projectUuid, String apiKey, String folderNameKeyword) {
        // 注意：此处假设媒体文件接口路径遵循 OpenAPI 规范
        // 如果实际文档路径不同，请在此处修改 url 字符串
        String url = djiApiBaseUrl + "/openapi/v0.1/workspaces/" + projectUuid + "/media-files?page=1&page_size=50";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-User-Token", apiKey)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                JsonNode root = objectMapper.readTree(json);
                JsonNode listNode = root.path("data").path("list");

                if (listNode.isArray()) {
                    List<DjiMediaFileDto> allFiles = objectMapper.convertValue(
                            listNode, new TypeReference<List<DjiMediaFileDto>>() {}
                    );

                    // 关键词过滤逻辑
                    if (folderNameKeyword == null || folderNameKeyword.isEmpty()) {
                        return allFiles;
                    }
                    List<DjiMediaFileDto> filtered = new ArrayList<>();
                    for (DjiMediaFileDto f : allFiles) {
                        if (f.getFilePath() != null && f.getFilePath().contains(folderNameKeyword)) {
                            filtered.add(f);
                        }
                    }
                    return filtered;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    // --- 私有辅助方法 ---

    private String fetchProjectsRaw(String apiKey) {
        // 路径：/openapi/v0.1/project
        String url = djiApiBaseUrl + "/openapi/v0.1/project";

        System.out.println("正在请求: " + url);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-User-Token", apiKey) // 使用正确的 Header
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            } else {
                System.err.println("API请求失败 Code: " + response.code());
                return null;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}