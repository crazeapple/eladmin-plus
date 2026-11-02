package com.eladminplus.auth.captcha;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 滑块验证码配置：图片尺寸、容差、票据与题目的留存时长。 */
@ConfigurationProperties(prefix = "auth.captcha")
public record CaptchaProperties(int imageWidth, int imageHeight, int tolerance, Duration ttl) {

    public CaptchaProperties {
        if (imageWidth <= 0) {
            imageWidth = 310;
        }
        if (imageHeight <= 0) {
            imageHeight = 155;
        }
        if (tolerance <= 0) {
            tolerance = 5;
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = Duration.ofMinutes(5);
        }
    }
}
