# 使用官方 OpenJDK 镜像作为基础镜像
FROM openjdk:17-jdk-slim

# 设置工作目录
WORKDIR /app

# 复制 Maven 打包后的 JAR 文件到容器中
COPY target/*.jar app.jar

# 暴露端口（默认 9995，可通过环境变量动态指定）
EXPOSE 9995

# 启动命令，使用环境变量覆盖配置
ENTRYPOINT ["java", "-jar", "app.jar"]