package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MvcResult;

/** /auth/me：用户 + 角色码 + 权限点 + 菜单树（root 种子 ROLE_ROOT 判定、菜单字段齐全）。 */
@SpringBootTest
class MeTest extends RefreshLogoutTest {

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Test
    void 用户区信息四块齐全且菜单树完整() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andReturn();
        status().isOk().match(result);

        JsonNode data = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data");
        // 用户视图
        assertThat(data.get("user").get("username").asText()).isEqualTo("admin");
        assertThat(data.get("user").get("nickName").asText()).isEqualTo("系统管理员");
        assertThat(data.get("user").has("password")).isFalse();
        // 角色码（ROLE_ROOT 内置位）
        assertThat(data.get("roles").toString()).contains("ROLE_ROOT");
        // 权限点清单存在（种子无按钮权限点，应为空数组）
        assertThat(data.get("permissions").isArray()).isTrue();

        // 菜单树：三条一级菜单，字段齐全、按 sort 升序
        JsonNode menus = data.get("menus");
        assertThat(menus.size()).isEqualTo(3);
        assertThat(menus.get(0).get("i18nKey").asText()).isEqualTo("routes.home");
        assertThat(menus.get(0).get("componentPathKey").asText()).isEqualTo("home/index");
        assertThat(menus.get(0).get("noCache").asBoolean()).isFalse();
        assertThat(menus.get(0).get("children").isArray()).isTrue();
        assertThat(menus.get(2).get("componentPathKey").asText()).isEqualTo("demo/no-cache");
        assertThat(menus.get(2).get("noCache").asBoolean()).isTrue();
        assertThat(menus.get(0).get("sort").asInt()).isLessThan(menus.get(2).get("sort").asInt());
    }

    @Test
    void me不在匿名清单未登录401() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me")).andReturn();
        status().isUnauthorized().match(result);
    }
}
