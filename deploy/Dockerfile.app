# 构建后端 JAR；前端 dist 构建后放入现有 Nginx webroot，后端 JAR 不内嵌前端静态资源
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml ./
COPY backend/ backend/
COPY runner/pom.xml runner/pom.xml
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
