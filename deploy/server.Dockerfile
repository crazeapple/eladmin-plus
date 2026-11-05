# 后端生产镜像：gradle wrapper 构建 bootJar，JRE 运行
# 构建上下文为仓库根（compose.yaml 的 context: ..）
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# 先拷构建脚本预热依赖缓存（依赖清单不变时源码变更不重拉依赖）
COPY apps/server/gradle apps/server/gradle
COPY apps/server/gradlew apps/server/settings.gradle.kts apps/server/build.gradle.kts apps/server/
WORKDIR /app/apps/server
RUN ./gradlew --no-daemon dependencies -q || true

COPY apps/server/src/ ./src/
RUN ./gradlew --no-daemon bootJar -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
# 手动备份经容器执行（docker exec 调 mysqldump）：运行层需 docker CLI（sock 挂载见 deploy/compose.yaml）
RUN apk add --no-cache docker-cli
COPY --from=build /app/apps/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
