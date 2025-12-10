package com.whu.ximaweb.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.dji.DjiAnnotationDto;
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

/**
 * DjiService 接口的实现类
 */
@Service
public class DjiServiceImpl implements DjiService {

    @Value("${dji.api.base-url}")
    private String apiBaseUrl; // 从配置文件读取基础URL (https://fh.dji.com/api/v1)

    @Autowired
    private OkHttpClient httpClient;

    @Autowired
    private ObjectMapper objectMapper; // 用于解析JSON

    @Override
    public List<DjiProjectDto> getProjects(String apiKey) {
        // 构造请求 URL: /workspaces (司空2中项目通常被称为 workspace)
        // 注意：根据文档 p371，公有云路径可能为 /workspaces 或 /openapi/v0.1/project，此处先使用标准REST风格
        // 如果实际运行报错，需调整为: apiBaseUrl + "/workspaces"
        String url = apiBaseUrl + "/workspaces";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", apiKey) // 司空2公有云通常使用 X-Auth-Token 或 X-Organization-Key
                .get()
                .build();

        return executeRequest(request, "list", new TypeReference<List<DjiProjectDto>>() {});
    }

    @Override
    public List<DjiAnnotationDto> getPolygonAnnotations(String projectUuid, String apiKey) {
        // 构造请求 URL: /workspaces/{id}/map-elements
        // 用于获取地图元素，后续需过滤出 Polygon 类型
        String url = String.format("%s/workspaces/%s/map-elements", apiBaseUrl, projectUuid);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", apiKey)
                .get()
                .build();

        // 获取后在业务层过滤，这里先返回所有标注
        return executeRequest(request, "list", new TypeReference<List<DjiAnnotationDto>>() {});
    }

    @Override
    public List<DjiMediaFileDto> getPhotosFromFolder(String projectUuid, String apiKey, String folderNameKeyword) {
        // 构造请求 URL: /workspaces/{id}/media-files
        // 可能需要分页参数，这里先简化为获取第一页或全部
        String url = String.format("%s/workspaces/%s/media-files?page=1&page_size=100", apiBaseUrl, projectUuid);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-Auth-Token", apiKey)
                .get()
                .build();

        List<DjiMediaFileDto> allFiles = executeRequest(request, "list", new TypeReference<List<DjiMediaFileDto>>() {});

        // 内存中过滤文件夹名称 (第一道防火墙)
        List<DjiMediaFileDto> filteredFiles = new ArrayList<>();
        if (allFiles != null) {
            for (DjiMediaFileDto file : allFiles) {
                if (file.getFilePath() != null && file.getFilePath().contains(folderNameKeyword)) {
                    filteredFiles.add(file);
                }
            }
        }
        return filteredFiles;
    }

    /**
     * 通用 HTTP 请求执行与 JSON 解析方法
     */
    private <T> List<T> executeRequest(Request request, String dataListNodeName, TypeReference<List<T>> typeReference) {
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String jsonBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(jsonBody);

                // 大疆API通常返回格式: { "code": 0, "data": { "list": [...] } }
                // 或者是: { "code": 0, "data": [...] }
                // 这里做简单的兼容处理
                JsonNode dataNode = rootNode.get("data");
                if (dataNode != null) {
                    if (dataNode.has(dataListNodeName)) {
                        String listJson = dataNode.get(dataListNodeName).toString();
                        return objectMapper.readValue(listJson, typeReference);
                    } else if (dataNode.isArray()) {
                        return objectMapper.readValue(dataNode.toString(), typeReference);
                    }
                }
                return Collections.emptyList();
            } else {
                System.err.println("大疆API调用失败: " + response.code() + " " + response.message());
                return Collections.emptyList();
            }
        } catch (IOException e) {
            System.err.println("网络请求异常: " + e.getMessage());
            return Collections.emptyList();
        }
    }
}