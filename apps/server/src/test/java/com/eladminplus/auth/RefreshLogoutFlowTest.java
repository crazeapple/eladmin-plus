package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.session.TokenService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

/** 刷新旋转与登出：宽限窗口并发语义、CSRF 附加层、幂等登出。 */
@SpringBootTest
class RefreshLogoutFlowTest extends RefreshLogoutTest {

    @org.springframework.beans.factory.annotation.Autowired
    TokenService tokenService;


    @Test
    void 刷新旋转发新对且旧键收窄进宽限() throws Exception {
        LoginSession session = loginSuccessfully();
        String oldCookie = session.refreshCookie();
        String[] parts = oldCookie.split("\\.");
        long ttlBefore = redis.getExpire(TokenService.refreshKey(Long.parseLong(parts[0]), parts[1]));
        assertThat(ttlBefore).isGreaterThan(60); // 全长 TTL（72h）

        MvcResult result = refresh(oldCookie, "XMLHttpRequest");
        status().isOk().match(result);

        String body = result.getResponse().getContentAsString();
        String newAccess = jsonField(body, "accessToken");
        String newCookie = cookieValue(result.getResponse().getHeader("Set-Cookie"));

        // 新对：access 全新、refresh 键全新且全长
        assertThat(newAccess).isNotEqualTo(session.accessToken());
        assertThat(newCookie).isNotEqualTo(oldCookie);
        String[] newParts = newCookie.split("\\.");
        assertThat(redis.opsForHash()
                .entries(TokenService.TOKEN_PREFIX + newAccess).get("username")).isEqualTo("admin");
        long newTtl = redis.getExpire(TokenService.refreshKey(Long.parseLong(newParts[0]), newParts[1]));
        assertThat(newTtl).isGreaterThan(60);

        // 旧键未删但收窄到宽限窗口（30s 量级）
        long oldTtl = redis.getExpire(TokenService.refreshKey(Long.parseLong(parts[0]), parts[1]));
        assertThat(oldTtl).isGreaterThan(0).isLessThanOrEqualTo(31);
    }

    @Test
    void 宽限窗口内旧refresh可再次旋转() throws Exception {
        LoginSession session = loginSuccessfully();
        String oldCookie = session.refreshCookie();

        refresh(oldCookie, "XMLHttpRequest");
        // 宽限内二次使用旧 cookie：仍然旋转成功（多 tab 场景）
        MvcResult second = refresh(oldCookie, "XMLHttpRequest");
        status().isOk().match(second);
        assertThat(jsonField(second.getResponse().getContentAsString(), "accessToken"))
                .isNotEqualTo(session.accessToken());
    }

    @Test
    void 并发双请求同旧refresh各得新对() throws Exception {
        LoginSession session = loginSuccessfully();
        String oldCookie = session.refreshCookie();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = pool.submit(() -> refresh(oldCookie, "XMLHttpRequest"));
            Future<MvcResult> second = pool.submit(() -> refresh(oldCookie, "XMLHttpRequest"));
            MvcResult r1 = first.get(30, TimeUnit.SECONDS);
            MvcResult r2 = second.get(30, TimeUnit.SECONDS);

            status().isOk().match(r1);
            status().isOk().match(r2);
            String a1 = jsonField(r1.getResponse().getContentAsString(), "accessToken");
            String a2 = jsonField(r2.getResponse().getContentAsString(), "accessToken");
            // 各得新对（前端单飞失效时的后端保险）
            assertThat(a1).isNotEqualTo(session.accessToken());
            assertThat(a2).isNotEqualTo(session.accessToken());
            assertThat(a1).isNotEqualTo(a2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 缺自定义头拒绝() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = refresh(session.refreshCookie(), null);
        status().isForbidden().match(result);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":403");
    }

    @Test
    void 无cookie与伪造cookie拒绝() throws Exception {
        MvcResult missing = refresh(null, "XMLHttpRequest");
        status().isUnauthorized().match(missing);

        MvcResult forged = refresh("999.forged-token-id.bad", "XMLHttpRequest");
        status().isUnauthorized().match(forged);
        assertThat(forged.getResponse().getContentAsString()).contains("\"code\":401");
    }

    @Test
    void 登出删会话与refresh且幂等() throws Exception {
        LoginSession session = loginSuccessfully();
        String[] parts = session.refreshCookie().split("\\.");

        MvcResult result = logout(session.accessToken());
        status().isOk().match(result);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":200");

        // 会话与 refresh 均被删
        assertThat(redis.hasKey(TokenService.TOKEN_PREFIX + session.accessToken())).isFalse();
        assertThat(redis.hasKey(TokenService.refreshKey(Long.parseLong(parts[0]), parts[1]))).isFalse();

        // 幂等登出：同一 token 再登出仍 200
        MvcResult again = logout(session.accessToken());
        status().isOk().match(again);

        // 登出后旧 access 即失效（强退语义同源）
        assertThat(tokenService.resolve(session.accessToken())).isNull();
    }
}
