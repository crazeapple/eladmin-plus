package com.eladminplus.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 运维备份配置：备份 sidecar 端点（compose 内网地址），随栈注入（本地 compose 与生产栈地址不同）。 */
@ConfigurationProperties(prefix = "ops.backup")
public record OpsBackupProperties(String sidecarUrl) {

    public OpsBackupProperties {
        sidecarUrl = sidecarUrl == null || sidecarUrl.isBlank() ? "http://localhost:9101" : sidecarUrl;
    }
}
