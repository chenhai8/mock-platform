# 构建前端静态资源，运行阶段由 Spring Boot 提供页面，不依赖 Nginx 容器
FROM node:24-bookworm-slim AS web
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build
# 将前端资源打包进业务 JAR，MySQL 驱动版本由 backend/pom.xml 管理
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY backend/ backend/
COPY runner/pom.xml runner/pom.xml
COPY --from=web /src/backend/src/main/resources/static backend/src/main/resources/static
RUN mvn -B -pl backend -am -DskipTests package
FROM eclipse-temurin:21-jre-jammy
RUN groupadd -g 10001 mocklab && useradd -u 10001 -g 10001 -M -s /usr/sbin/nologin mocklab
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/backend/target/backend-1.0.0.jar app.jar
USER 10001:10001
# 容器内固定使用 8080，宿主机端口通过 Compose 的 APP_PORT 配置
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java","-Xms128m","-Xmx512m","-jar","/app/app.jar"]
