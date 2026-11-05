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
        // 用户视图（资料三字段为个人中心表单预填数据源；admin 种子无值，null 也在册）
        assertThat(data.get("user").get("username").asText()).isEqualTo("admin");
        assertThat(data.get("user").get("nickName").asText()).isEqualTo("系统管理员");
        assertThat(data.get("user").has("avatarId")).isTrue();
        assertThat(data.get("user").has("email")).isTrue();
        assertThat(data.get("user").has("phone")).isTrue();
        assertThat(data.get("user").has("gender")).isTrue();
        assertThat(data.get("user").has("password")).isFalse();
        // 角色码（ROLE_ROOT 内置位）
        assertThat(data.get("roles").toString()).contains("ROLE_ROOT");
        // 权限点清单存在（V4 起种子含 18 条按钮权限点，ROLE_ROOT 直通全量）
        assertThat(data.get("permissions").isArray()).isTrue();

        // 菜单树：三条一级页面 + 系统管理目录（V4）+ 监控管理目录（V8）+ 工具目录（V10），按 sort 升序
        JsonNode menus = data.get("menus");
        assertThat(menus.size()).isEqualTo(6);
        assertThat(menus.get(0).get("i18nKey").asText()).isEqualTo("routes.home");
        assertThat(menus.get(0).get("componentPathKey").asText()).isEqualTo("home/index");
        assertThat(menus.get(0).get("noCache").asBoolean()).isFalse();
        assertThat(menus.get(0).get("children").isArray()).isTrue();
        assertThat(menus.get(2).get("componentPathKey").asText()).isEqualTo("demo/no-cache");
        assertThat(menus.get(2).get("noCache").asBoolean()).isTrue();
        assertThat(menus.get(0).get("sort").asInt()).isLessThan(menus.get(2).get("sort").asInt());
        // 系统管理目录（type=0），七个子菜单挂其下（V4 身份域四页 + V6 配置域三页）
        assertThat(menus.get(3).get("type").asInt()).isEqualTo(0);
        assertThat(menus.get(3).get("i18nKey").asText()).isEqualTo("routes.system");
        assertThat(menus.get(3).get("children").size()).isEqualTo(7);
        // 监控管理目录（type=0），四个子菜单挂其下（V8 监控域四页）
        assertThat(menus.get(4).get("type").asInt()).isEqualTo(0);
        assertThat(menus.get(4).get("i18nKey").asText()).isEqualTo("routes.monitor");
        assertThat(menus.get(4).get("children").size()).isEqualTo(4);
        // 末位为工具目录（type=0），三个子菜单挂其下（V10 工具域三页）
        assertThat(menus.get(5).get("type").asInt()).isEqualTo(0);
        assertThat(menus.get(5).get("i18nKey").asText()).isEqualTo("routes.tools");
        assertThat(menus.get(5).get("children").size()).isEqualTo(3);
    }

    @Test
    void me不在匿名清单未登录401() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/me")).andReturn();
        status().isUnauthorized().match(result);
    }
}
