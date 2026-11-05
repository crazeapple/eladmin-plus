package com.eladminplus.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 运维备份配置：mysql 容器名（docker exec 载体），随栈注入（本地 compose 与生产栈容器名不同）。 */
@ConfigurationProperties(prefix = "ops.backup")
public record OpsBackupProperties(String container) {

    public OpsBackupProperties {
        container = container == null || container.isBlank() ? "eladmin-plus-mysql" : container;
    }
}
