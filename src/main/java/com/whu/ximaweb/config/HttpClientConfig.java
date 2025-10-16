package com.whu.ximaweb.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HTTP客户端的配置类。
 */
@Configuration // @Configuration 注解表明这是一个Spring的配置类。
public class HttpClientConfig {

    /**
     * @Bean 注解告诉 Spring Boot：执行此方法，并将返回的对象作为一个 Bean 注册到 Spring 容器中。
     * 之后，任何地方通过 @Autowired 注入 OkHttpClient 时，Spring 都会提供这个方法创建的单例对象。
     * @return 一个可被全局使用的 OkHttpClient 实例。
     */
    @Bean
    public OkHttpClient okHttpClient() {
        // 创建并返回一个默认配置的 OkHttpClient 实例。
        return new OkHttpClient();
    }
}