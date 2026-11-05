package com.eladminplus.tools.alipay;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 支付宝配置位：带安全默认值，配置缺失不阻断启动（当前无实现 Bean，属性供真实集成消费）。 */
@ConfigurationProperties(prefix = "tools.alipay")
public record AlipayProperties(
        boolean enabled,
        String appId,
        String privateKey,
        String alipayPublicKey,
        String gatewayUrl,
        String signType,
        String notifyUrl,
        String returnUrl) {

    public AlipayProperties {
        appId = orDefault(appId, "");
        privateKey = orDefault(privateKey, "");
        alipayPublicKey = orDefault(alipayPublicKey, "");
        gatewayUrl = orDefault(gatewayUrl, "https://openapi.alipay.com/gateway");
        signType = orDefault(signType, "RSA2");
        notifyUrl = orDefault(notifyUrl, "");
        returnUrl = orDefault(returnUrl, "");
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
