package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 登录防护（小阈值配置下走真实端点）：同账号连续失败达阈值后锁定期内正确密码也拒且响应与
 * 密码错误同形（防枚举）；锁定到期自动解锁；成功登录清零计数；IP 滑动窗口限流超窗 429 统一
 * 错误结构；伪造 X-Forwarded-For 不换桶（对端地址计桶，头不生效）。
 */
@SpringBootTest(properties = {
    "auth.guard.acct-max-failures=3",
    "auth.guard.acct-lock-ttl=3s",
    "auth.guard.ip-window=3s",
    "auth.guard.ip-max-requests=30",
})
@AutoConfigureMockMvc
class LoginGuardTest {

    private static final String USERNAME = "guard-user";
    private static final String PASSWORD = "guard-pass-123";

    /** 防护键清理与到期等待的兜底时长（TTL 3s） */
    private static final long TTL_WAIT_MILLIS = 3_200;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        clearGuardKeys();
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getUsername, USERNAME));
        User user = new User();
        user.setUsername(USERNAME);
        user.setNickName("登录防护测试");
        user.setPassword(passwordEncoder.encode(PASSWORD));
        user.setEnabled(1);
        userMapper.insert(user);
    }

    @AfterEach
    void tearDown() {
        clearGuardKeys();
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getUsername, USERNAME));
    }

    /** 防护键全清（计数 / 锁定 / 限流窗口），用例间互不背残留态 */
    private void clearGuardKeys() {
        Set<String> keys = redis.keys("auth:guard:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private MvcResult attempt(String password) throws Exception {
        return LoginTestSupport.fullLogin(mockMvc, redis, USERNAME, password, null, null);
    }

    @Test
    void 连续失败达阈值后锁定期内正确密码也拒且与密码错同形() throws Exception {
        MvcResult wrongBody = null;
        for (int i = 0; i < 3; i++) {
            wrongBody = attempt("wrong-" + i);
            assertThat(wrongBody.getResponse().getStatus()).isEqualTo(401);
        }
        // 达阈值即锁定：锁定期内正确密码同样 401，响应体与密码错误完全同形（不区分文案）
        MvcResult locked = attempt(PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(401);
        assertThat(locked.getResponse().getContentAsString())
                .isEqualTo(wrongBody.getResponse().getContentAsString());
    }

    @Test
    void 锁定到期自动解锁() throws Exception {
        for (int i = 0; i < 3; i++) {
            attempt("wrong-" + i);
        }
        assertThat(attempt(PASSWORD).getResponse().getStatus()).isEqualTo(401);
        Thread.sleep(TTL_WAIT_MILLIS);
        assertThat(attempt(PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 成功登录清零计数() throws Exception {
        // 两轮「失败两次 + 成功一次」：若成功不清零，第二轮累计计数达阈值即锁，第二次成功必失败
        for (int round = 0; round < 2; round++) {
            assertThat(attempt("wrong-a" + round).getResponse().getStatus()).isEqualTo(401);
            assertThat(attempt("wrong-b" + round).getResponse().getStatus()).isEqualTo(401);
            assertThat(attempt(PASSWORD).getResponse().getStatus()).isEqualTo(200);
        }
    }

    @Test
    void IP限流超窗429统一结构且伪造转发头不换桶() throws Exception {
        // 每请求换一个伪造源：若服务端采信伪造头，各地址各桶永不超窗；429 出现即证对端地址计桶
        for (int i = 0; i < 30; i++) {
            mockMvc
                    .perform(get("/auth/captcha").header("X-Forwarded-For", "10.0.0." + (i + 1)))
                    .andExpect(status().isOk());
        }
        mockMvc
                .perform(get("/auth/captcha").header("X-Forwarded-For", "10.0.0.31"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").isNotEmpty());
        // 窗口滑动恢复：TTL 过窗后重新放行
        Thread.sleep(TTL_WAIT_MILLIS);
        mockMvc.perform(get("/auth/captcha")).andExpect(status().isOk());
    }
}
