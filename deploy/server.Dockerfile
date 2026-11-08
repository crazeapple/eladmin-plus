# 后端生产镜像：gradle wrapper 构建 bootJar，JRE 运行
# 构建上下文为仓库根（compose.yaml 的 context: ..）
FROM eclipse-temurin:21-jdk-alpine@sha256:82801f9dcdcbad5a05082aba8c86d1da5b72ccb498e71318b1f91b5a477887b3 AS build
WORKDIR /app

# 先拷构建脚本预热依赖缓存（依赖清单不变时源码变更不重拉依赖）
COPY apps/server/gradle apps/server/gradle
COPY apps/server/gradlew apps/server/settings.gradle.kts apps/server/build.gradle.kts apps/server/
WORKDIR /app/apps/server
RUN ./gradlew --no-daemon dependencies -q || true

COPY apps/server/src/ ./src/
RUN ./gradlew --no-daemon bootJar -q

FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474
WORKDIR /app
COPY --from=build /app/apps/server/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
