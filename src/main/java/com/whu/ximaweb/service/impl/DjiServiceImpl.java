package com.whu.ximaweb.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.whu.ximaweb.dto.dji.DjiMediaFileDto;
import com.whu.ximaweb.dto.dji.DjiProjectDto;
import com.whu.ximaweb.dto.dji.DjiTaskDto;
import com.whu.ximaweb.service.DjiService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    @Override
    public String getProjects() {
        return fetchProjectsRaw(this.defaultOrganizationKey);
    }

    @Override
    public List<DjiProjectDto> getProjects(String apiKey) {
        String json = fetchProjectsRaw(apiKey);
        if (json == null) return new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("code") && root.get("code").asInt() != 0) return new ArrayList<>();
            JsonNode listNode = root.path("data").path("list");
            if (listNode.isArray()) {
                return objectMapper.convertValue(listNode, new TypeReference<List<DjiProjectDto>>() {});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    private String fetchProjectsRaw(String apiKey) {
        String url = djiApiBaseUrl + "/openapi/v0.1/project?page=1&page_size=100";
        try (Response response = executeRequest(url, apiKey, null)) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<DjiMediaFileDto> getPhotosFromFolder(String projectUuid, String apiKey, String folderNameKeyword) {
        List<DjiMediaFileDto> resultList = new ArrayList<>();

        System.out.println("    [DEBUG] 🚀 开始全量扫描 (修正路径版)...");

        // 1. 获取设备
        String devicesUrl = djiApiBaseUrl + "/openapi/v0.1/project/device?page=1&page_size=100";
        Set<String> allDeviceSns = new HashSet<>();
        try (Response response = executeRequest(devicesUrl, apiKey, projectUuid)) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                JsonNode root = objectMapper.readTree(json);
                JsonNode listNode = root.path("data").path("list");
                if (listNode != null && listNode.isArray()) {
                    for (JsonNode deviceNode : listNode) {
                        JsonNode droneNode = deviceNode.path("drone");
                        if (!droneNode.isMissingNode() && droneNode.has("sn")) {
                            allDeviceSns.add(droneNode.get("sn").asText());
                        }
                        JsonNode gatewayNode = deviceNode.path("gateway");
                        if (!gatewayNode.isMissingNode() && gatewayNode.has("sn")) {
                            allDeviceSns.add(gatewayNode.get("sn").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
             e.printStackTrace();
        }

        if (allDeviceSns.isEmpty()) return Collections.emptyList();

        // 2. 查询任务
        long now = System.currentTimeMillis() / 1000;
        long endTime = now + 24 * 60 * 60;
        long startTime = now - 90 * 24 * 60 * 60;

        // 准备时间格式化器：将 UTC 转换为 北京时间格式 (yyyy-MM-dd HH_mm_ss)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH_mm_ss");

        for (String sn : allDeviceSns) {
            String taskListUrl = djiApiBaseUrl + "/openapi/v0.1/flight-task/list" +
                    "?page=1&page_size=50" +
                    "&begin_at=" + startTime +
                    "&end_at=" + endTime +
                    "&sn=" + sn;

            try (Response response = executeRequest(taskListUrl, apiKey, projectUuid)) {
                if (response.isSuccessful() && response.body() != null) {
                    String json = response.body().string();
                    JsonNode root = objectMapper.readTree(json);
                    JsonNode listNode = root.path("data").path("list");

                    if (listNode != null && listNode.isArray()) {
                        List<DjiTaskDto> tasks = objectMapper.convertValue(listNode, new TypeReference<List<DjiTaskDto>>() {});

                        for (DjiTaskDto task : tasks) {
                            boolean nameMatched = folderNameKeyword == null || (task.getName() != null && task.getName().contains(folderNameKeyword));
                            boolean statusMatched = !"failed".equalsIgnoreCase(task.getStatus());

                            if (nameMatched && statusMatched) {
                                // 获取媒体文件
                                String mediaUrl = djiApiBaseUrl + "/openapi/v0.1/flight-task/" + task.getUuid() + "/media";
                                try (Response mediaResp = executeRequest(mediaUrl, apiKey, projectUuid)) {
                                    if (mediaResp.isSuccessful() && mediaResp.body() != null) {
                                        String mediaJson = mediaResp.body().string();
                                        JsonNode mediaRoot = objectMapper.readTree(mediaJson);
                                        JsonNode mediaList = mediaRoot.path("data").path("list");

                                        if (mediaList != null && mediaList.isArray()) {
                                            List<DjiMediaFileDto> files = objectMapper.convertValue(mediaList, new TypeReference<List<DjiMediaFileDto>>() {});

                                            // ✅ 核心修正：构造与本地SD卡一致的文件夹名称
                                            // 1. 解析 UTC 时间
                                            String beginAt = task.getBeginAt(); // 2025-12-12T05:29:02...Z
                                            String safeTimeStr;
                                            try {
                                                if (beginAt != null) {
                                                    ZonedDateTime utcTime = ZonedDateTime.parse(beginAt);
                                                    // 2. 转换为北京时间 (Asia/Shanghai)
                                                    ZonedDateTime cstTime = utcTime.withZoneSameInstant(ZoneId.of("Asia/Shanghai"));
                                                    // 3. 格式化为 "2025-12-12 13_29_02"
                                                    safeTimeStr = cstTime.format(formatter) + " (UTC+08)";
                                                } else {
                                                    safeTimeStr = "unknown-time";
                                                }
                                            } catch (Exception ex) {
                                                safeTimeStr = beginAt.replaceAll("[: ]", "-"); // 兜底
                                            }

                                            // 最终路径: /激光测距/激光测距 2025-12-12 13_29_02 (UTC+08)
                                            String virtualPath = "/" + folderNameKeyword + "/" + task.getName() + " " + safeTimeStr;

                                            for (DjiMediaFileDto f : files) {
                                                f.setFilePath(virtualPath);
                                                resultList.add(f);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return resultList;
    }

    private Response executeRequest(String url, String apiKey, String projectUuid) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("X-User-Token", apiKey);
        if (projectUuid != null && !projectUuid.isEmpty()) {
            builder.addHeader("X-Project-Uuid", projectUuid);
        }
        return httpClient.newCall(builder.build()).execute();
    }
}