package com.whu.ximaweb.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 萤石云对接服务
 * 负责调用开放平台接口获取 AccessToken
 */
@Service
public class EzvizService {

    @Autowired
    private OkHttpClient okHttpClient;

    @Autowired
    private ObjectMapper objectMapper;

    // 萤石云获取Token的官方接口地址
    private static final String EZVIZ_TOKEN_URL = "https://open.ys7.com/api/lapp/token/get";

    /**
     * 调用萤石云接口，换取 AccessToken
     * @param appKey 应用Key
     * @param appSecret 应用密钥
     * @return 成功返回 Token 字符串，失败抛出异常
     */
    public String getAccessToken(String appKey, String appSecret) throws Exception {
        if (appKey == null || appSecret == null) {
            throw new IllegalArgumentException("AppKey 或 Secret 不能为空");
        }

        // 构造表单参数
        FormBody body = new FormBody.Builder()
                .add("appKey", appKey)
                .add("appSecret", appSecret)
                .build();

        Request request = new Request.Builder()
                .url(EZVIZ_TOKEN_URL)
                .post(body)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("请求萤石云接口失败: HTTP " + response.code());
            }

            String respStr = response.body().string();
            // 解析 JSON
            JsonNode root = objectMapper.readTree(respStr);
            String code = root.path("code").asText();

            // code "200" 代表成功
            if ("200".equals(code)) {
                JsonNode data = root.path("data");
                String accessToken = data.path("accessToken").asText();
                // 你也可以在这里获取 expireTime，默认有效期是7天
                return accessToken;
            } else {
                String msg = root.path("msg").asText();
                throw new RuntimeException("萤石云认证失败: " + msg);
            }
        }
    }
}