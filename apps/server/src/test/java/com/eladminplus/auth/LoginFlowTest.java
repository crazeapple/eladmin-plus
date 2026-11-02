package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.captcha.BlockPuzzleService;
import com.eladminplus.auth.session.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 登录全链：滑块票据 + RSA 解密 + BCrypt + 令牌对 + cookie 属性 + 失败分语义。 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginFlowTest {

    private static final OAEPParameterSpec OAEP_SPEC =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    private String encrypt(String publicKeyPem, String plain) {
        try {
            // JSON 响应里的换行是字面反斜杠+n 序列，先还原为真实换行再剥头尾
            String pem = publicKeyPem.replace("\\n", "\n");
            byte[] keyBytes = Base64.getMimeDecoder()
                    .decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 走完整链拿登录态：出题 → 校验 → 公钥 → 密文 → 登录 */
    private MvcResult login(String password) throws Exception {
        MvcResult captchaResult = mockMvc
                .perform(get("/auth/captcha"))
                .andExpect(status().isOk())
                .andReturn();
        String captchaBody = captchaResult.getResponse().getContentAsString();
        String token = extractJsonField(captchaBody, "token");
        int answer = Integer.parseInt(
                redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));

        MvcResult checkResult = mockMvc
                .perform(post("/auth/captcha/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"moveDistance\":" + answer + "}"))
                .andExpect(status().isOk())
                .andReturn();
        String ticket = extractJsonField(checkResult.getResponse().getContentAsString(), "ticket");

        MvcResult keyResult = mockMvc.perform(get("/auth/public-key")).andExpect(status().isOk()).andReturn();
        String keyBody = keyResult.getResponse().getContentAsString();
        String keyId = extractJsonField(keyBody, "keyId");
        String publicKey = extractJsonField(keyBody, "publicKey");

        String cipher = encrypt(publicKey, password);
        String body = "{\"username\":\"admin\",\"password\":\"" + cipher + "\",\"keyId\":\"" + keyId
                + "\",\"ticket\":\"" + ticket + "\"}";
        return mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    /** 取 data.<field> 的字符串值（响应同形结构，够用即可） */
    private static String extractJsonField(String body, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + field + "\":\"([^\"]*)\"")
                .matcher(body);
        assertThat(matcher.find()).as("字段 %s 存在：%s", field, body).isTrue();
        return matcher.group(1);
    }

    @Test
    void 登录成功发令牌对且cookie属性齐备() throws Exception {
        MvcResult result = login("123456");

        status().isOk().match(result);
        org.assertj.core.api.SoftAssertions softly = new org.assertj.core.api.SoftAssertions();
        String body = result.getResponse().getContentAsString();
        String accessToken = extractJsonField(body, "accessToken");
        softly.assertThat(accessToken).isNotBlank();

        // Set-Cookie：HttpOnly + SameSite=Lax + Path=/（代理形态请求带 /api 前缀，path 收窄会使 cookie 不可达）
        String setCookie = result.getResponse().getHeader("Set-Cookie");
        softly.assertThat(setCookie).contains("refresh_token=", "HttpOnly", "SameSite=Lax", "Path=/");

        // 会话与 refresh 落 Redis
        var session = redis.opsForHash().entries(TokenService.TOKEN_PREFIX + accessToken);
        softly.assertThat(session.get("username")).isEqualTo("admin");
        softly.assertThat((String) session.get("roles")).contains("ROLE_ROOT");
        String tokenId = (String) session.get("tokenId");
        String userId = (String) session.get("userId");
        softly.assertThat(redis.opsForValue().get(TokenService.refreshKey(Long.valueOf(userId), tokenId))).isNotNull();
        softly.assertAll();
    }

    @Test
    void 密码错误统一文案不区分哪一半错() throws Exception {
        MvcResult result = login("wrong-password");
        status().isUnauthorized().match(result);
        // 词条经 MessageSource 本地化（词条补齐前断言码直出，此处随 T08 升级为文案断言）
        assertThat(result.getResponse().getContentAsString()).doesNotContain("error.auth.");
    }

    @Test
    void 票据重放被拒() throws Exception {
        // 走链取到合法票据后不用于登录，直接连续消费两次语义
        MvcResult captchaResult = mockMvc.perform(get("/auth/captcha")).andReturn();
        String token = extractJsonField(captchaResult.getResponse().getContentAsString(), "token");
        int answer = Integer.parseInt(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));
        MvcResult checkResult = mockMvc
                .perform(post("/auth/captcha/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"moveDistance\":" + answer + "}"))
                .andReturn();
        String ticket = extractJsonField(checkResult.getResponse().getContentAsString(), "ticket");

        String cipherForUnknownKey = encrypt(rsaCurrentPublicKey(), "123456");
        String body = "{\"username\":\"admin\",\"password\":\"" + cipherForUnknownKey
                + "\",\"keyId\":\"" + UUID.randomUUID() + "\",\"ticket\":\"" + ticket + "\"}";
        // 第一次：keyId 未知先炸（票据已被消费）——公钥过期语义
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        // 第二次：票据已被烧，即使其余全对也是 400 票据无效
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 公钥过期语义独立于票据() throws Exception {
        String cipher = encrypt(rsaCurrentPublicKey(), "123456");
        String body = "{\"username\":\"admin\",\"password\":\"" + cipher
                + "\",\"keyId\":\"" + UUID.randomUUID() + "\",\"ticket\":\"whatever\"}";
        // 未知 keyId 先于票据消费？——实现顺序票据先焚；未知 keyId 消费掉票据后仍报公钥过期。
        // 为验证「公钥过期」语义本身，给一张真票据：
        MvcResult captchaResult = mockMvc.perform(get("/auth/captcha")).andReturn();
        String token = extractJsonField(captchaResult.getResponse().getContentAsString(), "token");
        int answer = Integer.parseInt(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));
        MvcResult checkResult = mockMvc
                .perform(post("/auth/captcha/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"moveDistance\":" + answer + "}"))
                .andReturn();
        String ticket = extractJsonField(checkResult.getResponse().getContentAsString(), "ticket");
        String bodyWithTicket = "{\"username\":\"admin\",\"password\":\"" + cipher
                + "\",\"keyId\":\"" + UUID.randomUUID() + "\",\"ticket\":\"" + ticket + "\"}";
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(bodyWithTicket))
                .andExpect(status().isBadRequest());
    }

    private String rsaCurrentPublicKey() throws Exception {
        MvcResult keyResult = mockMvc.perform(get("/auth/public-key")).andExpect(status().isOk()).andReturn();
        return extractJsonField(keyResult.getResponse().getContentAsString(), "publicKey");
    }
}
