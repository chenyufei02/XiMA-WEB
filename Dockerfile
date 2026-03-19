# 1. 使用包含 Java 21 环境的轻量级基础镜像
FROM eclipse-temurin:21-jre-alpine

# 2. 设定系统时区为上海（极其重要，防止你的监控日志和日报时间错乱）
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 3. 在容器内创建一个工作目录
WORKDIR /app

# 4. 把你将来打包生成的 jar 包，拷贝到容器里并改名为 app.jar
COPY target/*.jar app.jar

# 5. 声明你的后端服务使用的是 8080 端口
EXPOSE 8080

# 6. 容器启动时执行的命令（相当于在黑框框里敲 java -jar 运行程序）
ENTRYPOINT ["java", "-jar", "app.jar"]