package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.common.translation.Translation;
import com.eladminplus.common.translation.TranslationMapper;
import com.eladminplus.system.domain.RoleDept;
import com.eladminplus.system.dto.RoleSaveRequest;
import com.eladminplus.system.mapper.RoleDeptMapper;
import java.nio.charset.StandardCharsets;
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

/** 角色域：分页、CRUD（多语言、数据范围与自定义部门集合）、删除与越权校验、内置角色保护。 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private RoleDeptMapper roleDeptMapper;
    @Autowired
    private TranslationMapper translationMapper;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        List<Long> testIds = roleMapper
                .selectList(Wrappers.<Role>lambdaQuery().like(Role::getCode, "ROLE_TEST_"))
                .stream()
                .map(Role::getId)
                .toList();
        for (Long id : testIds) {
            roleDeptMapper.delete(Wrappers.<RoleDept>lambdaQuery().eq(RoleDept::getRoleId, id));
            translationMapper.delete(Wrappers.<Translation>lambdaQuery()
                    .eq(Translation::getEntity, "role")
                    .eq(Translation::getEntityId, id));
            roleMapper.deleteById(id);
        }
    }

    private Long createRole(String code, String name, String nameEn, int level, String dataScope, List<Long> deptIds)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSaveRequest(
                                null, code, name, nameEn, "测试描述", level, dataScope, deptIds, 1))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    @Test
    void 新增多语言角色与自定义部门集合() throws Exception {
        Long deptId = 1L;
        Long roleId = createRole("ROLE_TEST_CUSTOM", "测试角色", "Test Role", 50, "CUSTOM", List.of(deptId));

        // en 展示覆盖名;CUSTOM 档下发部门集合
        MvcResult en = mockMvc.perform(get("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", "en"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper
                .readTree(en.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data")
                .get("content");
        JsonNode hit = null;
        for (JsonNode row : rows) {
            if (row.get("id").asLong() == roleId) {
                hit = row;
            }
        }
        assertThat(hit).isNotNull();
        assertThat(hit.get("name").asText()).isEqualTo("Test Role");
        assertThat(hit.get("dataScope").asText()).isEqualTo("CUSTOM");
        assertThat(hit.get("customDeptIds").get(0).asLong()).isEqualTo(deptId);
    }

    @Test
    void code与默认名唯一被友好拒绝() throws Exception {
        createRole("ROLE_TEST_DUP", "测试唯一名", null, 50, "ALL", null);
        MvcResult dupCode = mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RoleSaveRequest(null, "ROLE_TEST_DUP", "另一个名", null, null, 50, "ALL", null, 1))))
                .andReturn();
        assertThat(dupCode.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(dupCode.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .get("message")
                        .asText())
                .isEqualTo("角色码已存在");

        MvcResult dupName = mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RoleSaveRequest(null, "ROLE_TEST_DUP2", "测试唯一名", null, null, 50, "ALL", null, 1))))
                .andReturn();
        assertThat(dupName.getResponse().getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(dupName.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .get("message")
                        .asText())
                .isEqualTo("角色名已存在");
    }

    @Test
    void 内置角色不可删除且编辑不改code与level() throws Exception {
        Role root = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, "ROLE_ROOT"));
        Long rootId = root.getId();
        Integer levelBefore = root.getLevel();

        mockMvc.perform(delete("/roles/{id}", rootId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("内置角色不能删除"));

        mockMvc.perform(put("/roles/{id}", rootId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSaveRequest(
                                rootId, "ROLE_HACKED", "内置超级管理员", null, null, 1, "ALL", null, 1))))
                .andExpect(status().isOk());

        Role after = roleMapper.selectById(rootId);
        assertThat(after.getCode()).isEqualTo("ROLE_ROOT");
        assertThat(after.getLevel()).isEqualTo(levelBefore); // 内置角色 level 不可改(保持原值)
        assertThat(after.getCode()).isNotEqualTo("ROLE_HACKED");
    }

    @Autowired
    private com.eladminplus.auth.mapper.UserRoleMapper userRoleMapper;

    @Test
    void 关联用户的角色删除被拒() throws Exception {
        Long roleId = createRole("ROLE_TEST_INUSE", "在用角色", null, 60, "ALL", null);
        // admin 挂该角色(测完清理)
        var admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        var userRole = new com.eladminplus.auth.domain.UserRole();
        userRole.setUserId(admin.getId());
        userRole.setRoleId(roleId);
        userRoleMapper.insert(userRole);

        mockMvc.perform(delete("/roles/{id}", roleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("角色下存在用户，不能删除"));

        userRoleMapper.delete(Wrappers.<com.eladminplus.auth.domain.UserRole>lambdaQuery()
                .eq(com.eladminplus.auth.domain.UserRole::getRoleId, roleId));
        mockMvc.perform(delete("/roles/{id}", roleId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }
}
