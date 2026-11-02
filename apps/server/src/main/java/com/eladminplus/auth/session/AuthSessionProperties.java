package com.eladminplus.auth.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 会话与凭证配置：双 token 时长、旋转宽限窗口、refresh cookie 属性。 */
@ConfigurationProperties(prefix = "auth.session")
public record AuthSessionProperties(
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration graceWindow,
        String cookieName,
        String cookiePath,
        String cookieSameSite,
        boolean cookieSecure) {

    public AuthSessionProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()) {
            accessTokenTtl = Duration.ofHours(2);
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isNegative() || refreshTokenTtl.isZero()) {
            refreshTokenTtl = Duration.ofHours(72);
        }
        if (graceWindow == null || graceWindow.isNegative() || graceWindow.isZero()) {
            graceWindow = Duration.ofSeconds(30);
        }
        cookieName = (cookieName == null || cookieName.isBlank()) ? "refresh_token" : cookieName;
        cookiePath = (cookiePath == null || cookiePath.isBlank()) ? "/auth" : cookiePath;
        cookieSameSite = (cookieSameSite == null || cookieSameSite.isBlank()) ? "Lax" : cookieSameSite;
    }
}
