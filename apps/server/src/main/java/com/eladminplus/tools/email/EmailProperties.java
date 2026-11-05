package com.eladminplus.tools.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 邮件工具配置：发件人（SMTP 主机等连接参数走 spring.mail.*，未配置即邮件服务未启用）。 */
@ConfigurationProperties(prefix = "tools.email")
public record EmailProperties(String from) {

    public EmailProperties {
        from = (from == null || from.isBlank()) ? "eladmin-plus@noreply.local" : from;
    }
}
