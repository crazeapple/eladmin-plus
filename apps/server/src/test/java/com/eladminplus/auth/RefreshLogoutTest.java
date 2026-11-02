package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.eladminplus.auth.captcha.BlockPuzzleService;
import com.eladminplus.auth.session.TokenService;
import jakarta.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 登录 / 刷新 / 登出测试公共链路。 */
@AutoConfigureMockMvc
abstract class RefreshLogoutTest {

    static final Pattern FIELD_PATTERN = Pattern.compile("\"(\\w+)\":\"([^\"]*)\"");

    static final OAEPParameterSpec OAEP_SPEC =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    @Autowired
    MockMvc mockMvc;

    @Autowired
    StringRedisTemplate redis;

    /** 登录产物：accessToken + refresh cookie 原值 */
    record LoginSession(String accessToken, String refreshCookie) {}

    /** 完整登录链：出题 → 校验 → 公钥 → RSA 密文 → 登录，返回会话 */
    LoginSession loginSuccessfully() throws Exception {
        MvcResult captchaResult = mockMvc.perform(get("/auth/captcha")).andReturn();
        String token = jsonField(captchaResult.getResponse().getContentAsString(), "token");
        int answer = Integer.parseInt(redis.opsForValue().get(BlockPuzzleService.PUZZLE_PREFIX + token));

        MvcResult checkResult = mockMvc
                .perform(post("/auth/captcha/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + token + "\",\"moveDistance\":" + answer + "}"))
                .andReturn();
        String ticket = jsonField(checkResult.getResponse().getContentAsString(), "ticket");

        MvcResult keyResult = mockMvc.perform(get("/auth/public-key")).andReturn();
        String keyBody = keyResult.getResponse().getContentAsString();
        String keyId = jsonField(keyBody, "keyId");
        String publicKey = jsonField(keyBody, "publicKey").replace("\\n", "\n");

        String cipher = encrypt(publicKey, "123456");
        MvcResult loginResult = mockMvc
                .perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"" + cipher + "\",\"keyId\":\"" + keyId
                                + "\",\"ticket\":\"" + ticket + "\"}"))
                .andReturn();
        String body = loginResult.getResponse().getContentAsString();
        String accessToken = jsonField(body, "accessToken");
        String setCookie = loginResult.getResponse().getHeader("Set-Cookie");
        return new LoginSession(accessToken, cookieValue(setCookie));
    }

    /** 携 cookie（可带自定义头）刷新，返回响应 */
    MvcResult refresh(String refreshCookie, @Nullable String requestedWith) throws Exception {
        var builder = post("/auth/refresh");
        if (requestedWith != null) {
            builder = builder.header("X-Requested-With", requestedWith);
        }
        if (refreshCookie != null) {
            builder = builder.cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshCookie));
        }
        return mockMvc.perform(builder).andReturn();
    }

    MvcResult logout(String accessToken) throws Exception {
        return mockMvc
                .perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andReturn();
    }

    static String jsonField(String body, String field) {
        Matcher matcher = FIELD_PATTERN.matcher(body);
        while (matcher.find()) {
            if (matcher.group(1).equals(field)) {
                return matcher.group(2);
            }
        }
        throw new IllegalStateException("字段缺失：" + field + " in " + body);
    }

    /** Set-Cookie 头里取 cookie 值（name=value; 属性…） */
    static String cookieValue(String setCookie) {
        return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';'));
    }

    private static String encrypt(String publicKeyPem, String plain) {
        try {
            byte[] keyBytes = Base64.getMimeDecoder()
                    .decode(publicKeyPem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SPEC);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 并发跑同一段调用（多 tab 单飞失效场景） */
    static Future<MvcResult> concurrent(ExecutorService pool, Callable<MvcResult> task) {
        return pool.submit(task);
    }
}
