package com.whu.ximaweb.config;

import com.obs.services.ObsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 华为云OBS的配置类。
 * 这个类的作用是读取 application.properties 文件中的配置信息，
 * 并基于这些信息创建一个可以被整个应用程序使用的 ObsClient 实例（Bean）。
 */
@Configuration // @Configuration 注解表明这是一个Spring的配置类。
public class HuaweiObsConfig {

    // @Value 注解会自动从 application.properties 文件中读取相应键的值，并注入到这个字段中。
    @Value("${huawei.obs.endpoint}")
    private String endpoint;

    @Value("${huawei.obs.ak}")
    private String accessKey;

    @Value("${huawei.obs.sk}")
    private String secretKey;

    /**
     * @Bean 注解告诉 Spring Boot：请执行这个方法，并将它返回的对象放入 Spring 容器中管理。
     * 这样，其他任何需要使用 ObsClient 的地方，都可以通过依赖注入（比如 @Autowired）来获取它。
     * @return 一个配置好、随时可以使用的 ObsClient 实例。
     */
    @Bean
    public ObsClient obsClient() {
        // 使用从配置文件中读取到的密钥和地址，创建一个新的 ObsClient 对象。
        return new ObsClient(accessKey, secretKey, endpoint);
    }
}