package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.dto.RoleSaveRequest;
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

/** 角色菜单授权：全量菜单树端点、已授权回显、保存即生效。 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleMenuTest {

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
    private UserRoleMapper userRoleMapper;
    @Autowired
    private com.eladminplus.auth.mapper.RoleMenuMapper roleMenuMapper;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        Role test = roleMapper.selectOne(Wrappers.<Role>lambdaQuery().eq(Role::getCode, "ROLE_TEST_MENU"));
        if (test != null) {
            roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, test.getId()));
            roleMapper.deleteById(test.getId());
        }
    }

    @Test
    void 全量树端点与授权保存即时生效() throws Exception {
        MvcResult tree = mockMvc.perform(get("/menus/tree")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode nodes = objectMapper.readTree(tree.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
        assertThat(nodes.size()).isGreaterThanOrEqualTo(3);

        MvcResult createResult = mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSaveRequest(
                                null, "ROLE_TEST_MENU", "菜单授权测试角色", null, null, 50, "ALL", null, 1))))
                .andExpect(status().isOk())
                .andReturn();
        Long roleId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("data")
                .asLong();

        List<Long> menuIds = List.of(nodes.get(0).get("id").asLong(), nodes.get(1).get("id").asLong());
        mockMvc.perform(put("/roles/{id}/menus", roleId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(menuIds)))
                .andExpect(status().isOk());

        MvcResult echo = mockMvc.perform(get("/roles/{id}/menus", roleId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode echoIds =
                objectMapper.readTree(echo.getResponse().getContentAsString(StandardCharsets.UTF_8)).get("data");
        assertThat(echoIds.size()).isEqualTo(2);
        assertThat(echoIds.get(0).asLong()).isEqualTo(menuIds.get(0));
    }

    @Test
    void 授权过的角色删除时菜单关联级联清理() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/roles")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RoleSaveRequest(
                                null, "ROLE_TEST_MENU", "级联清理测试角色", null, null, 60, "ALL", null, 1))))
                .andExpect(status().isOk())
                .andReturn();
        Long roleId = objectMapper
                .readTree(createResult.getResponse().getContentAsString())
                .get("data")
                .asLong();

        mockMvc.perform(put("/roles/{id}/menus", roleId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(1L, 2L))))
                .andExpect(status().isOk());
        assertThat(roleMenuMapper
                .selectCount(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId)))
                .isEqualTo(2);

        // 授权关联不清即触发外键约束（回归位：角色删除须级联清 role_menu）
        mockMvc.perform(delete("/roles/{id}", roleId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(roleMapper.selectById(roleId)).isNull();
        assertThat(roleMenuMapper
                .selectCount(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getRoleId, roleId)))
                .isZero();
    }
}
