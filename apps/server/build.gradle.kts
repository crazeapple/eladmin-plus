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
    // 数据访问：MyBatis-Plus（Boot 4 适配 starter）与 MySQL 驱动（版本随 Boot BOM）
    implementation("com.baomidou:mybatis-plus-spring-boot4-starter:3.5.18.1")
    // 分页等 inner interceptor 自 3.5.9 拆到独立构件，与 starter 同版
    implementation("com.baomidou:mybatis-plus-jsqlparser:3.5.18.1")
    runtimeOnly("com.mysql:mysql-connector-j")
    // 数据库迁移：Boot 4 模块化后自动配置在 spring-boot-flyway；MySQL 方言支持在独立构件（版本随 Boot BOM）
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-mysql")
    // 定时任务：Boot 4 模块化后自动配置在 spring-boot-quartz；JDBC JobStore 表组由 Flyway 管理
    implementation("org.springframework.boot:spring-boot-quartz")
    // 监控域：Excel 导出（easyexcel）、系统与 JVM 指标采集（oshi-core）、连接池指标读取（micrometer-core）
    implementation("com.alibaba:easyexcel:4.0.3")
    implementation("com.github.oshi:oshi-core:6.8.2")
    implementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
