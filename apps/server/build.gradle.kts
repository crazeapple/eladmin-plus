plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.springdoc.openapi-gradle-plugin") version "1.8.0"
}

group = "com.eladminplus"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // 接口契约：springdoc 提供 OpenAPI 描述与 swagger-ui，构建期由 gradle 插件导出
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0")
    // Boot 4 默认 Jackson 3；spring-data-redis 的 JSON 序列化器仍基于 Jackson 2，显式引入（版本随 BOM）
    implementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
