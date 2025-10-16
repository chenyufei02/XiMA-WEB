package com.whu.ximaweb.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 这是一个测试用的控制器（Controller）。
 * 在 Spring Boot 中，控制器的作用是接收来自网络的请求，并给出响应。
 */
@RestController // @RestController 注解告诉 Spring Boot，这个类是用来处理 Web 请求的。
@RequestMapping("/api") // @RequestMapping("/api") 表示这个类里的所有接口，地址都以 "/api" 开头。
public class HelloController {

    /**
     * @GetMapping("/hello") 注解定义了一个处理 GET 请求的接口。
     * 它的完整地址是 /api/hello。
     * 当我们用浏览器访问 http://localhost:8080/api/hello 时，这个方法就会被执行。
     * @return 这个方法返回一个简单的字符串 "Hello, World!"。Spring Boot 会自动把它作为响应内容发送给浏览器。
     */
    @GetMapping("/hello")
    public String sayHello() {
        return "Hello, XiMA-WEB!";
    }
}