package com.whu.ximaweb;
// http://localhost:8080/swagger-ui.html
// HPUABUTLBIQT1VINPPP3
// jEaQow9nst1qtfF5mVqdCPtQmoW26nje00RPIi72
// eyJhbGciOiJIUzUxMiIsImNyaXQiOlsidHlwIiwiYWxnIiwia2lkIl0sImtpZCI6IjhiZmRiZmRkLWM4OGYtNGE5Yi04NzI3LWQ0ZGYzYWE5OTJlOSIsInR5cCI6IkpXVCJ9.eyJhY2NvdW50IjoiMTUxNzE1ODU3MzciLCJleHAiOjIwNzYyMDA2MzQsIm5iZiI6MTc2MDY2NzgzNCwib3JnYW5pemF0aW9uX3V1aWQiOiJmNmQyMmYyZi04YjFkLTQ3YjYtYmQ0Mi1jZDQzZDdhZmVjNzAiLCJwcm9qZWN0X3V1aWQiOiIiLCJzdWIiOiJmaDIiLCJ1c2VyX2lkIjoiMTk0NTg0NTAzNjg3NDQzNjYwOCJ9.1Nxib22lheq7ZBba_9wM0IdFDKSgi3P1UIPkommMabc0cbhKAXbIAW1eDoYQssJm29b1j8CW0WrDpXkA92KvMA
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@MapperScan("com.whu.ximaweb.mapper")  // 指定 Mapper 接口所在的包路径
@SpringBootApplication
public class XiMaWebApplication {

	public static void main(String[] args) {
		SpringApplication.run(XiMaWebApplication.class, args);
	}

}
