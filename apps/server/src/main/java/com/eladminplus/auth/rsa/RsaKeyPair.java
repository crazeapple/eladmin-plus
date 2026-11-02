package com.eladminplus.auth.rsa;

import java.time.Instant;

/** 当前生效的 RSA 密钥对（PEM 全文）。keyId 随公钥同发，密文回传用于解密定位。 */
public record RsaKeyPair(String keyId, String publicKeyPem, String privateKeyPem, Instant createdAt) {}
