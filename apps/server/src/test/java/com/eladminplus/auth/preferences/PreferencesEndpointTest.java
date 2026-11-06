package com.eladminplus.auth.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 偏好端点：me 响应带偏好四项（NULL 即缺省）、PUT 合法持久、非法 400 逐字段、未登录 401。 */
@SpringBootTest
@AutoConfigureMockMvc
class PreferencesEndpointTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private PreferencesService preferencesService;
    @Autowired
    private UserMapper userMapper;

    private Long adminId;
    private String adminPreferencesOriginal;

    @BeforeEach
    void snapshotAdmin() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        adminId = admin.getId();
        adminPreferencesOriginal = admin.getPreferences();
    }

    @AfterEach
    void restoreAdmin() {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, adminId)
                .set(User::getPreferences, adminPreferencesOriginal));
    }

    /** 直发会话令牌（登录链路另有专测，此处只取身份面） */
    private String token() {
        User admin = userMapper.selectById(adminId);
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    @Test
    void me响应含偏好四项_NULL列回缺省() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + token()))
                .andReturn();
        status().isOk().match(result);

        JsonNode preferences = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("preferences");
        assertThat(preferences.get("theme").asText()).isEqualTo(UserPreferences.DEFAULT_THEME);
        assertThat(preferences.get("primaryColor").asText()).isEqualTo(UserPreferences.DEFAULT_PRIMARY_COLOR);
        assertThat(preferences.get("locale").asText()).isEqualTo(UserPreferences.DEFAULT_LOCALE);
        assertThat(preferences.get("iconSet").asText()).isEqualTo(UserPreferences.DEFAULT_ICON_SET);
    }

    @Test
    void me响应偏好与库内持久值一致() throws Exception {
        preferencesService.update(adminId, new PreferencesUpdateRequest("dark", "#123abc", "en", "ep"));

        MvcResult result = mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + token()))
                .andReturn();
        status().isOk().match(result);

        JsonNode preferences = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .get("preferences");
        assertThat(preferences.get("theme").asText()).isEqualTo("dark");
        assertThat(preferences.get("primaryColor").asText()).isEqualTo("#123abc");
        assertThat(preferences.get("locale").asText()).isEqualTo("en");
        assertThat(preferences.get("iconSet").asText()).isEqualTo("ep");
    }

    @Test
    void PUT合法_200且持久幂等() throws Exception {
        String token = token();
        String body = "{\"theme\":\"dark\",\"primaryColor\":\"#abcdef\",\"locale\":\"en\",\"iconSet\":\"ep\"}";

        mockMvc
                .perform(put("/profile/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        User written = userMapper.selectById(adminId);
        assertThat(preferencesService.of(written))
                .isEqualTo(new UserPreferences("dark", "#abcdef", "en", "ep"));

        // 幂等：同值重写仍 200 且读回不变
        mockMvc
                .perform(put("/profile/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        assertThat(preferencesService.of(userMapper.selectById(adminId)))
                .isEqualTo(new UserPreferences("dark", "#abcdef", "en", "ep"));
    }

    @Test
    void PUT非法_400逐字段且库值不变() throws Exception {
        String token = token();
        String body = "{\"theme\":\"blue\",\"primaryColor\":\"red\",\"locale\":\"en\",\"iconSet\":\"ep\"}";

        MvcResult result = mockMvc
                .perform(put("/profile/preferences")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        status().isBadRequest().match(result);
        JsonNode envelope = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(envelope.get("code").asInt()).isEqualTo(400);
        assertThat(envelope.get("message").asText()).contains("theme", "primaryColor").doesNotContain("locale");
        assertThat(userMapper.selectById(adminId).getPreferences()).isEqualTo(adminPreferencesOriginal);
    }

    @Test
    void PUT未登录_401() throws Exception {
        mockMvc
                .perform(put("/profile/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"theme\":\"dark\",\"primaryColor\":\"#123abc\",\"locale\":\"en\",\"iconSet\":\"svg\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
