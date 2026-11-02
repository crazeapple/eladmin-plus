package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MvcResult;

/** 平台过滤链：permitAll 清单、401/403 语义、@PreAuthorize 权限点载体。 */
@SpringBootTest
@Import(TestProtectedController.class)
class SecurityFilterChainTest extends RefreshLogoutTest {

    @Test
    void 受保护路径无令牌401同形结构() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth-test-protected/authenticated-only")).andReturn();
        status().isUnauthorized().match(result);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"code\":401");
        assertThat(body).doesNotContain("error.auth.");
    }

    @Test
    void 伪造令牌同样401() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/auth-test-protected/authenticated-only")
                        .header("Authorization", "Bearer forged-token"))
                .andReturn();
        status().isUnauthorized().match(result);
    }

    @Test
    void 有效令牌放行已认证端点() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = mockMvc
                .perform(get("/auth-test-protected/authenticated-only")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andReturn();
        status().isOk().match(result);
        assertThat(result.getResponse().getContentAsString()).contains("\"ok\":\"yes\"");
    }

    @Test
    void 权限点不满足403载体生效() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = mockMvc
                .perform(get("/auth-test-protected/needs-permission")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andReturn();
        status().isForbidden().match(result);
        assertThat(result.getResponse().getContentAsString()).contains("\"code\":403");
    }

    @Test
    void 角色位直通载体生效() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = mockMvc
                .perform(get("/auth-test-protected/needs-root")
                        .header("Authorization", "Bearer " + session.accessToken()))
                .andReturn();
        status().isOk().match(result);
        // principal 为登录用户名
        assertThat(result.getResponse().getContentAsString()).contains("admin");
    }

    @Test
    void permitAll清单不受过滤链影响() throws Exception {
        // 登录链路端点与统计占位端点保持匿名可达
        status().isOk().match(mockMvc.perform(get("/auth/public-key")).andReturn());
        status().isOk().match(mockMvc.perform(get("/stats/overview")).andReturn());
    }
}
