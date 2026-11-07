package com.eladminplus.auth.guard;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 登录防护配置：账号失败锁定（阈值 / 锁定时长）与 IP 限流（滑动窗口 / 上限）。 */
@ConfigurationProperties(prefix = "auth.guard")
public record AuthGuardProperties(
        int acctMaxFailures, Duration acctLockTtl, Duration ipWindow, int ipMaxRequests) {

    public AuthGuardProperties {
        if (acctMaxFailures <= 0) {
            acctMaxFailures = 5;
        }
        if (acctLockTtl == null || acctLockTtl.isNegative() || acctLockTtl.isZero()) {
            acctLockTtl = Duration.ofMinutes(15);
        }
        if (ipWindow == null || ipWindow.isNegative() || ipWindow.isZero()) {
            ipWindow = Duration.ofMinutes(1);
        }
        if (ipMaxRequests <= 0) {
            ipMaxRequests = 60;
        }
    }
}
