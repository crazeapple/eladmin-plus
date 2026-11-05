package com.eladminplus.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** 存储域配置：引擎选择（local 本地磁盘 / rustfs 对象存储，重启生效）、上传大小限制与双引擎连接参数。 */
@ConfigurationProperties(prefix = "storage")
public record StorageProperties(String engine, DataSize maxSize, Local local, Rustfs rustfs) {

    public StorageProperties {
        engine = (engine == null || engine.isBlank()) ? "rustfs" : engine;
        if (maxSize == null || maxSize.isNegative() || maxSize.toBytes() == 0) {
            maxSize = DataSize.ofMegabytes(50);
        }
        local = local == null ? new Local(null) : local;
        local = new Local(orDefault(local.basePath(), "./data/storage"));
        rustfs = rustfs == null ? new Rustfs(null, null, null, null) : rustfs;
        rustfs = new Rustfs(
                orDefault(rustfs.endpoint(), "http://localhost:9100"),
                orDefault(rustfs.bucket(), "eladmin-plus"),
                orDefault(rustfs.accessKey(), "rustfsadmin"),
                orDefault(rustfs.secretKey(), "rustfsadmin"));
    }

    /** 本地磁盘引擎：根目录配置化 */
    public record Local(String basePath) {}

    /** RustFS（S3 兼容）引擎：端点、桶名与凭据配置化 */
    public record Rustfs(String endpoint, String bucket, String accessKey, String secretKey) {}

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
