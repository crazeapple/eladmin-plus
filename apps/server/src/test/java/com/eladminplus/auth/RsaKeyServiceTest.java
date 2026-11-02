package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.rsa.RsaKeyService;
import com.eladminplus.common.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** RSA 密钥链路：换代、公钥下发、retired 兜底、过期与格式校验语义。 */
@SpringBootTest
@AutoConfigureMockMvc
class RsaKeyServiceTest {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    /** OAEP 参数与服务端解密一致（SHA-256 / MGF1-SHA256），模拟前端 Web Crypto 加密 */
    private static final OAEPParameterSpec OAEP_SPEC =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    @Autowired
    private RsaKeyService rsaKeyService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MockMvc mockMvc;

    private String encrypt(String publicKeyPem, String plain) throws Exception {
        byte[] keyBytes = Base64.getMimeDecoder()
                .decode(publicKeyPem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void 启动装配后current在Redis且含完整四字段() {
        var hash = redis.opsForHash().entries(RsaKeyService.CURRENT_KEY);
        // 多测试上下文并存时各自启动换代，Redis 的 current 以最后启动者为准——断言键自洽，不与内存镜像互锁
        assertThat((String) hash.get("keyId")).matches(UUID_PATTERN);
        assertThat((String) hash.get("publicKey")).contains("BEGIN PUBLIC KEY");
        assertThat((String) hash.get("privateKey")).contains("BEGIN PRIVATE KEY");
        assertThat(hash.get("createdAt")).isNotNull();
    }

    @Test
    void 公钥端点下发keyId与公钥() throws Exception {
        mockMvc.perform(get("/auth/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.keyId").value(rsaKeyService.current().keyId()))
                .andExpect(jsonPath("$.data.publicKey").value(rsaKeyService.current().publicKeyPem()));
    }

    @Test
    void 换代后旧公钥密文经retired兜底仍可解() throws Exception {
        // 换代前的密文：用旧公钥加密
        var oldPair = rsaKeyService.current();
        String cipherOfOld = encrypt(oldPair.publicKeyPem(), "在途登录明文");

        // 启动装配换代（模拟服务重启）
        rsaKeyService.rotate();
        assertThat(rsaKeyService.current().keyId()).isNotEqualTo(oldPair.keyId());

        // 旧私钥已落 retired 且带 TTL
        Long ttl = redis.getExpire(RsaKeyService.RETIRED_PREFIX + oldPair.keyId());
        assertThat(ttl).isNotNull().isGreaterThan(0);

        // 旧 keyId 定位解密成功（在途无感）
        assertThat(rsaKeyService.decrypt(cipherOfOld, oldPair.keyId())).isEqualTo("在途登录明文");
    }

    @Test
    void 未知keyId返回公钥已过期语义() {
        String unknownUuid = UUID.randomUUID().toString();
        assertThatThrownBy(() -> rsaKeyService.decrypt("ciphertext", unknownUuid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.auth.public-key-expired");
    }

    @Test
    void 非uuid格式keyId直接拒绝不触达Redis() {
        assertThatThrownBy(() -> rsaKeyService.decrypt("ciphertext", "not-a-uuid'"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.auth.key-id-invalid");
        // 伪造 retired 键不存在于该非法 keyId 名下（格式校验先于任何点查）
        assertThat(redis.opsForValue().get(RsaKeyService.RETIRED_PREFIX + "not-a-uuid'")).isNull();
    }
}
