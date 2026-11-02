package com.eladminplus.auth.rsa;

import com.eladminplus.common.error.BusinessException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * RSA 登录密钥服务：密钥对存 Redis 双槽（current + retired），启动装配时换代——
 * 重启即轮换，且重启窗口的在途登录经 retired 兜底无感。解密先比对 current.keyId
 * （正常路径零额外读），不匹配点查 retired，皆无即「公钥已过期」。
 */
@Service
public class RsaKeyService {

    public static final String CURRENT_KEY = "auth:rsa:current";
    public static final String RETIRED_PREFIX = "auth:rsa:retired:";

    /** OAEP：主哈希与 MGF1 均 SHA-256，与前端 Web Crypto RSA-OAEP(SHA-256) 对齐 */
    private static final OAEPParameterSpec OAEP_SPEC =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    /** keyId 仅接受 UUID 形态，防 Redis key 探测 */
    private static final Pattern KEY_ID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final StringRedisTemplate redis;
    private final RsaKeyProperties props;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 进程内镜像：换代只发生在启动装配（单实例），读取零 Redis 开销 */
    private volatile RsaKeyPair current;

    public RsaKeyService(StringRedisTemplate redis, RsaKeyProperties props) {
        this.redis = redis;
        this.props = props;
    }

    @PostConstruct
    public void rotateOnStartup() {
        rotate();
    }

    /** 换代：旧私钥先落 retired（EX），新对后覆写 current——kill -9 也不破「在途无感」语义 */
    public synchronized void rotate() {
        Map<Object, Object> old = redis.opsForHash().entries(CURRENT_KEY);
        if (!old.isEmpty()) {
            String oldKeyId = (String) old.get("keyId");
            String oldPrivateKey = (String) old.get("privateKey");
            redis.opsForValue()
                    .set(RETIRED_PREFIX + oldKeyId, oldPrivateKey, props.retiredTtl().getSeconds(), TimeUnit.SECONDS);
        }
        this.current = generateAndStore();
    }

    public RsaKeyPair current() {
        return current;
    }

    /** 公钥端点载荷：keyId 与公钥同发 */
    public Map<String, String> publicKeyPayload() {
        return Map.of("keyId", current.keyId(), "publicKey", current.publicKeyPem());
    }

    /**
     * RSA-OAEP 解密登录密文。keyId 非法格式直接拒绝（防 Redis key 探测）；
     * current 与 retired 皆无法解出 → 400「公钥已过期」，前端据此自动重取公钥。
     */
    public String decrypt(String cipherBase64, String keyId) {
        if (keyId == null || !KEY_ID_PATTERN.matcher(keyId).matches()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.key-id-invalid");
        }
        if (keyId.equals(current.keyId())) {
            String plain = tryDecrypt(current.privateKeyPem(), cipherBase64);
            if (plain == null) {
                // keyId 指向当前密钥而解密失败 = 密文损坏，属请求参数问题
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
            }
            return plain;
        }
        String retiredPrivateKey = redis.opsForValue().get(RETIRED_PREFIX + keyId);
        if (retiredPrivateKey != null) {
            String plain = tryDecrypt(retiredPrivateKey, cipherBase64);
            if (plain == null) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.public-key-expired");
            }
            return plain;
        }
        throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.public-key-expired");
    }

    /** 解密实现；失败返回 null（密钥与密文不匹配），由调用方定语义 */
    private String tryDecrypt(String privateKeyPem, String cipherBase64) {
        try {
            byte[] keyBytes = Base64.getMimeDecoder().decode(pemBody(privateKeyPem));
            PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
            // 带 ECB 的 OAEP 转换名自 JDK 19 起标记弃用，但仍是 OAEP 参数的标准载体，语义明确
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SPEC);
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(cipherBase64));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception failed) {
            return null;
        }
    }

    private RsaKeyPair generateAndStore() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(props.keySize(), secureRandom);
            KeyPair pair = generator.generateKeyPair();
            String publicKeyPem = toPem("PUBLIC KEY", pair.getPublic().getEncoded());
            String privateKeyPem = toPem("PRIVATE KEY", pair.getPrivate().getEncoded());
            RsaKeyPair keyPair = new RsaKeyPair(UUID.randomUUID().toString(), publicKeyPem, privateKeyPem, Instant.now());

            Map<String, String> hash = new HashMap<>();
            hash.put("keyId", keyPair.keyId());
            hash.put("publicKey", publicKeyPem);
            hash.put("privateKey", privateKeyPem);
            hash.put("createdAt", keyPair.createdAt().toString());
            redis.opsForHash().putAll(CURRENT_KEY, hash);
            return keyPair;
        } catch (Exception e) {
            throw new IllegalStateException("生成 RSA 密钥对失败", e);
        }
    }

    private String toPem(String type, byte[] der) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + base64 + "\n-----END " + type + "-----";
    }

    /** PEM 剥头尾取 DER Base64 主体 */
    private String pemBody(String pem) {
        return pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
    }
}
