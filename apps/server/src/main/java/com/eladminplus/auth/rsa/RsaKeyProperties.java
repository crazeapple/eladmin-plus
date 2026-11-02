package com.eladminplus.auth.rsa;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 认证 RSA 配置：密钥位长与退役私钥留存时长。 */
@ConfigurationProperties(prefix = "auth.rsa")
public record RsaKeyProperties(int keySize, Duration retiredTtl) {

    public RsaKeyProperties {
        if (keySize <= 0) {
            keySize = 2048;
        }
        if (retiredTtl == null || retiredTtl.isNegative() || retiredTtl.isZero()) {
            retiredTtl = Duration.ofSeconds(600);
        }
    }
}
