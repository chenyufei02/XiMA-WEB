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

        System.out.println("    [DEBUG] 🚀 开始全量扫描 (机场+无人机双重扫描)...");

        // --- 第一步：获取设备列表 ---
        String devicesUrl = djiApiBaseUrl + "/openapi/v0.1/project/device?page=1&page_size=100";
        System.out.println("    [DEBUG] 请求设备列表: " + devicesUrl);

        Set<String> allDeviceSns = new HashSet<>();
        try (Response response = executeRequest(devicesUrl, apiKey, projectUuid)) {
            if (response.isSuccessful() && response.body() != null) {
                String json = response.body().string();
                JsonNode root = objectMapper.readTree(json);
                JsonNode listNode = root.path("data").path("list");
                if (listNode != null && listNode.isArray()) {
                    for (JsonNode deviceNode : listNode) {
                        // 1. 尝试获取无人的机 SN (Drone)
                        JsonNode droneNode = deviceNode.path("drone");
                        if (!droneNode.isMissingNode() && droneNode.has("sn")) {
                            String sn = droneNode.get("sn").asText();
                            String name = droneNode.path("device_model").path("name").asText("未知飞机");
                            System.out.println("       🚁 发现飞机: " + name + " [SN: " + sn + "]");
                            allDeviceSns.add(sn);
                        }

                        // 2. 尝试获取机场的 SN (Gateway/Dock) - ✅ 关键修正！
                        JsonNode gatewayNode = deviceNode.path("gateway");
                        if (!gatewayNode.isMissingNode() && gatewayNode.has("sn")) {
                            String sn = gatewayNode.get("sn").asText();
                            String name = gatewayNode.path("device_model").path("name").asText("未知机场");
                            System.out.println("       🏠 发现机场: " + name + " [SN: " + sn + "]");
                            allDeviceSns.add(sn);
                        }
                    }
                }
            } else {
                 System.err.println("    [ERROR] 获取设备失败. HTTP Code: " + response.code());
            }
        } catch (Exception e) {
             System.err.println("    [EXCEPTION] 获取设备异常: " + e.getMessage());
        }

        if (allDeviceSns.isEmpty()) {
            System.out.println("    ⚠️ 未发现任何设备SN，流程终止。");
            return Collections.emptyList();
        }

        // --- 第二步：查询任务 ---
        long now = System.currentTimeMillis() / 1000;
        long endTime = now + 24 * 60 * 60;
        long startTime = now - 90 * 24 * 60 * 60;

        for (String sn : allDeviceSns) {
            String taskListUrl = djiApiBaseUrl + "/openapi/v0.1/flight-task/list" +
                    "?page=1&page_size=50" +
                    "&begin_at=" + startTime +
                    "&end_at=" + endTime +
                    "&sn=" + sn;

            System.out.println("    --------------------------------------------------");
            System.out.println("    [DEBUG] 查询设备 [" + sn + "] 的任务...");

            try (Response response = executeRequest(taskListUrl, apiKey, projectUuid)) {
                if (response.body() != null) {
                    String json = response.body().string();
                    // 打印 RAW JSON 以便确认
                    // System.out.println("    [RAW_JSON] " + json);

                    if (response.isSuccessful()) {
                        JsonNode root = objectMapper.readTree(json);
                        JsonNode listNode = root.path("data").path("list");

                        if (listNode != null && listNode.isArray()) {
                            List<DjiTaskDto> tasks = objectMapper.convertValue(listNode, new TypeReference<List<DjiTaskDto>>() {});
                            System.out.println("    📄 找到 " + tasks.size() + " 个任务");

                            for (DjiTaskDto task : tasks) {
                                boolean nameMatched = folderNameKeyword == null || (task.getName() != null && task.getName().contains(folderNameKeyword));
                                boolean statusMatched = !"failed".equalsIgnoreCase(task.getStatus());

                                System.out.print("       > 检查 [" + task.getName() + "] (" + task.getStatus() + ")");

                                if (nameMatched && statusMatched) {
                                    System.out.println(" -> ✅ 命中! 下载中...");
                                    String mediaUrl = djiApiBaseUrl + "/openapi/v0.1/flight-task/" + task.getUuid() + "/media";
                                    try (Response mediaResp = executeRequest(mediaUrl, apiKey, projectUuid)) {
                                        if (mediaResp.isSuccessful() && mediaResp.body() != null) {
                                            String mediaJson = mediaResp.body().string();
                                            JsonNode mediaRoot = objectMapper.readTree(mediaJson);
                                            JsonNode mediaList = mediaRoot.path("data").path("list");
                                            if (mediaList != null && mediaList.isArray()) {
                                                List<DjiMediaFileDto> files = objectMapper.convertValue(mediaList, new TypeReference<List<DjiMediaFileDto>>() {});

                                                System.out.println("         📸 发现 " + files.size() + " 张照片");

                                                String safeTime = (task.getBeginAt() != null) ? task.getBeginAt().replaceAll("[: ]", "-") : "unknown";
                                                String virtualPath = "/" + folderNameKeyword + "/" + task.getName() + "_" + safeTime;
                                                for (DjiMediaFileDto f : files) {
                                                    f.setFilePath(virtualPath);
                                                    resultList.add(f);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    System.out.println(" -> 跳过");
                                }
                            }
                        } else {
                             System.out.println("    ⚠️ 无任务 (list=null/empty)");
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