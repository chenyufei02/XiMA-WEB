package com.whu.ximaweb;
// http://localhost:8080/swagger-ui.html
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
