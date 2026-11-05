package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** 连接配置端点：增改查删生效；密码不回显、更新留空保持原值；必填缺失 400；无权限 403；未知 id 404。 */
@SpringBootTest
@AutoConfigureMockMvc
class DatabaseConfigCrudTest {

    /** 测试行备注前缀，清理定位用 */
    private static final String TEST_PREFIX = "crud-test-";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DatabaseConfigMapper databaseConfigMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        databaseConfigMapper.delete(
                Wrappers.<DatabaseConfig>lambdaQuery().likeRight(DatabaseConfig::getRemark, TEST_PREFIX));
    }

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    private String createBody(String password) {
        return """
                {"host":"localhost","port":3307,"name":"eladmin_plus","userName":"root","password":"%s","remark":"%s样例"}
                """
                .formatted(password == null ? "" : password, TEST_PREFIX);
    }

    /** 新增一条测试配置，返回行 id */
    private Long createRow() throws Exception {
        String body = mockMvc
                .perform(post("/database/configs")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("root")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").asLong();
    }

    @Test
    void 新增与详情生效且密码不回显() throws Exception {
        String token = rootToken();
        MvcBody created = postConfigs(token, createBody("secret-pass"), status().isOk());
        Long id = created.id();
        assertThat(created.json().path("data").asLong()).isPositive();

        String detail = mockMvc
                .perform(get("/database/configs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode view = objectMapper.readTree(detail).path("data");
        assertThat(view.path("host").asText()).isEqualTo("localhost");
        assertThat(view.path("port").asInt()).isEqualTo(3307);
        assertThat(view.path("name").asText()).isEqualTo("eladmin_plus");
        assertThat(view.path("userName").asText()).isEqualTo("root");
        assertThat(view.has("password")).isFalse();
        assertThat(detail).doesNotContain("secret-pass");

        String list = mockMvc
                .perform(get("/database/configs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(list).doesNotContain("password").doesNotContain("secret-pass");
    }

    @Test
    void 更新留空密码保持原值() throws Exception {
        String token = rootToken();
        Long id = postConfigs(token, createBody("keep-pass"), status().isOk()).id();

        mockMvc
                .perform(put("/database/configs/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT password FROM database_config WHERE id = ?", String.class, id))
                .isEqualTo("keep-pass");

        mockMvc
                .perform(put("/database/configs/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("new-pass")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT password FROM database_config WHERE id = ?", String.class, id))
                .isEqualTo("new-pass");
    }

    @Test
    void 删除生效且未知id404() throws Exception {
        String token = rootToken();
        Long id = postConfigs(token, createBody("root"), status().isOk()).id();

        mockMvc.perform(delete("/database/configs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(databaseConfigMapper.selectById(id)).isNull();
        mockMvc.perform(get("/database/configs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/database/configs/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/database/configs/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("root")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 必填与形态校验400() throws Exception {
        String token = rootToken();
        // 新增密码必填
        mockMvc
                .perform(post("/database/configs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("连接密码不能为空"));
        // 必填缺失与端口越界
        postConfigs(token, "{\"host\":\"\",\"port\":3307,\"name\":\"eladmin_plus\",\"userName\":\"root\",\"password\":\"x\",\"remark\":\""
                        + TEST_PREFIX
                        + "bad\"}",
                status().isBadRequest());
        postConfigs(token, "{\"host\":\"localhost\",\"port\":0,\"name\":\"eladmin_plus\",\"userName\":\"root\",\"password\":\"x\",\"remark\":\""
                        + TEST_PREFIX
                        + "bad\"}",
                status().isBadRequest());
    }

    @Test
    void 无权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/database/configs").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/database/configs")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("root")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/database/configs/1")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("root")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/database/configs/1").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }

    /** 发起新增请求，返回解析后的响应（data 为新行 id） */
    private MvcBody postConfigs(String token, String body, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        String content = mockMvc
                .perform(post("/database/configs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(expected)
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(content);
        return new MvcBody(json, json.path("data").asLong());
    }

    /** 响应解析载体：原始树 + data.id 便捷读取 */
    private record MvcBody(JsonNode json, Long id) {}
}
