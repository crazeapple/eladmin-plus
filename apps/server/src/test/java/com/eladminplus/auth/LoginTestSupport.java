package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.captcha.BlockPuzzleService;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 完整登录链测试辅助：出题 → 校验 → 公钥 → RSA-OAEP 密文 → 登录（返回原始响应供断言）。 */
public final class LoginTestSupport {

    private static final javax.crypto.spec.OAEPParameterSpec OAEP_SPEC =
            new javax.crypto.spec.OAEPParameterSpec(
                    "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256,
                    javax.crypto.spec.PSource.PSpecified.DEFAULT);

    private LoginTestSupport() {}

    /** 走完整登录链;响应码与体由调用方断言(401 拒绝语义 / 200 成功形态)。 */
    public static MvcResult fullLogin(MockMvc mockMvc, StringRedisTemplate redis, String username, String password)
            throws Exception {
        return fullLogin(mockMvc, redis, username, password, null, null);
    }

    /** 带转发头与 UA 的完整登录链（在线用户域断言 IP / UA 补落用；传 null 即不发该头）。 */
    public static MvcResult fullLogin(
            MockMvc mockMvc, StringRedisTemplate redis, String username, String password,
            String forwardedFor, String userAgent) throws Exception {
        MvcResult captchaResult = mockMvc.perform(get("/auth/captcha")).andExpect(status().isOk()).andReturn();
        String captchaBody = captchaResult.getResponse().getContentAsString();
        String token = extractJsonField(captchaBody, "token");
        int answer = Integer.parseInt(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));

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
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + cipher + "\",\"keyId\":\"" + keyId
                + "\",\"ticket\":\"" + ticket + "\"}";
        var request = post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body);
        if (forwardedFor != null) {
            request = request.header("X-Forwarded-For", forwardedFor);
        }
        if (userAgent != null) {
            request = request.header("User-Agent", userAgent);
        }
        return mockMvc.perform(request).andReturn();
    }

    private static String encrypt(String publicKeyPem, String plain) {
        try {
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

    public static String extractJsonField(String body, String field) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("\"" + field + "\":\"([^\"]*)\"").matcher(body);
        assertThat(matcher.find()).as("字段 %s 存在：%s", field, body).isTrue();
        return matcher.group(1);
    }
}
