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
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.dto.MenuSaveRequest;
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

/** 菜单管理 CRUD：全量树（含按钮节点）、icon 与 hidden 生效、删除校验 409、type 不可变 422、权限拦截 403。 */
@SpringBootTest
@AutoConfigureMockMvc
class MenuCrudTest {

    /** 测试菜单 i18n key 前缀，清理定位用 */
    private static final String TEST_KEY_PREFIX = "testcrud.";

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
    private RoleMenuMapper roleMenuMapper;
    @Autowired
    private com.eladminplus.auth.mapper.MenuMapper menuMapper;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        // 先清角色关联再清测试菜单（i18n key 前缀定位），不残留授权死链
        List<Long> testIds = menuMapper
                .selectList(Wrappers.<com.eladminplus.auth.domain.Menu>lambdaQuery()
                        .likeRight(com.eladminplus.auth.domain.Menu::getI18nKey, TEST_KEY_PREFIX))
                .stream()
                .map(com.eladminplus.auth.domain.Menu::getId)
                .toList();
        for (Long id : testIds) {
            roleMenuMapper.delete(Wrappers.<RoleMenu>lambdaQuery().eq(RoleMenu::getMenuId, id));
            menuMapper.deleteById(id);
        }
    }

    private Long create(MenuSaveRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/menus")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private JsonNode tree() throws Exception {
        String body = mockMvc.perform(get("/menus/tree").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data");
    }

    private JsonNode findById(JsonNode node, Long id) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode hit = findById(item, id);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (node.get("id").asLong() == id) {
            return node;
        }
        for (JsonNode child : node.get("children")) {
            JsonNode hit = findById(child, id);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Test
    void 树含按钮节点且三型新增入树() throws Exception {
        JsonNode before = tree();
        assertThat(hasTypeNode(before, 2)).isTrue(); // 种子已含按钮权限点节点

        Long dirId = create(new MenuSaveRequest(
                null, null, 0, null, TEST_KEY_PREFIX + "dir", null, null, false, false, 9));
        Long menuId = create(new MenuSaveRequest(
                null, dirId, 1, null, TEST_KEY_PREFIX + "menu", "system/demo", null, false, false, 1));
        Long btnId = create(new MenuSaveRequest(
                null, menuId, 2, "system:demo:list", TEST_KEY_PREFIX + "btn", null, null, false, false, 1));

        JsonNode dirNode = findById(tree(), dirId);
        assertThat(dirNode).isNotNull();
        assertThat(dirNode.get("type").asInt()).isEqualTo(0);
        assertThat(dirNode.get("children").size()).isEqualTo(1);
        JsonNode menuNode = dirNode.get("children").get(0);
        assertThat(menuNode.get("id").asLong()).isEqualTo(menuId);
        assertThat(menuNode.get("type").asInt()).isEqualTo(1);
        assertThat(menuNode.get("children").get(0).get("id").asLong()).isEqualTo(btnId);
        assertThat(menuNode.get("children").get(0).get("permission").asText()).isEqualTo("system:demo:list");
    }

    @Test
    void 编辑icon与hidden生效且可清空() throws Exception {
        Long menuId = create(new MenuSaveRequest(
                null, null, 1, null, TEST_KEY_PREFIX + "icon", "system/demo", null, false, false, 5));

        mockMvc.perform(put("/menus/{id}", menuId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuSaveRequest(
                                null, null, 1, null, TEST_KEY_PREFIX + "icon", "system/demo", "setting", true,
                                false, 5))))
                .andExpect(status().isOk());
        JsonNode edited = findById(tree(), menuId);
        assertThat(edited.get("icon").asText()).isEqualTo("setting");
        assertThat(edited.get("hidden").asBoolean()).isTrue();

        // 全量表单提交语义：icon 置 null 即清空，hidden 翻回 false
        mockMvc.perform(put("/menus/{id}", menuId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuSaveRequest(
                                null, null, 1, null, TEST_KEY_PREFIX + "icon", "system/demo", null, false,
                                false, 5))))
                .andExpect(status().isOk());
        JsonNode cleared = findById(tree(), menuId);
        assertThat(cleared.get("icon").isNull()).isTrue();
        assertThat(cleared.get("hidden").asBoolean()).isFalse();
    }

    @Test
    void 删除校验有子或角色关联被拒409() throws Exception {
        Long dirId = create(new MenuSaveRequest(
                null, null, 0, null, TEST_KEY_PREFIX + "dir2", null, null, false, false, 9));
        Long childId = create(new MenuSaveRequest(
                null, dirId, 1, null, TEST_KEY_PREFIX + "child", "system/demo", null, false, false, 1));

        mockMvc.perform(delete("/menus/{id}", dirId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("存在子菜单，不能删除"));

        mockMvc.perform(delete("/menus/{id}", childId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 角色关联保护：任意既有角色挂上该菜单后删除被拒
        Long leafId = create(new MenuSaveRequest(
                null, null, 1, null, TEST_KEY_PREFIX + "leaf", "system/demo", null, false, false, 6));
        Role anyRole = roleMapper.selectList(Wrappers.<Role>lambdaQuery().last("LIMIT 1")).get(0);
        RoleMenu grant = new RoleMenu();
        grant.setRoleId(anyRole.getId());
        grant.setMenuId(leafId);
        roleMenuMapper.insert(grant);

        mockMvc.perform(delete("/menus/{id}", leafId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("菜单已授权角色，不能删除"));
    }

    @Test
    void type变更编辑被拒422() throws Exception {
        Long dirId = create(new MenuSaveRequest(
                null, null, 0, null, TEST_KEY_PREFIX + "dir3", null, null, false, false, 9));
        mockMvc.perform(put("/menus/{id}", dirId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuSaveRequest(
                                null, null, 1, null, TEST_KEY_PREFIX + "dir3", "system/demo", null, false,
                                false, 9))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("菜单类型创建后不可变更"));
    }

    @Test
    void 无add权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(post("/menus")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MenuSaveRequest(
                                null, null, 1, null, TEST_KEY_PREFIX + "noauth", "system/demo", null, false,
                                false, 9))))
                .andExpect(status().isForbidden());
    }

    private boolean hasTypeNode(JsonNode node, int type) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                if (hasTypeNode(item, type)) {
                    return true;
                }
            }
            return false;
        }
        if (node.get("type").asInt() == type) {
            return true;
        }
        for (JsonNode child : node.get("children")) {
            if (hasTypeNode(child, type)) {
                return true;
            }
        }
        return false;
    }
}
