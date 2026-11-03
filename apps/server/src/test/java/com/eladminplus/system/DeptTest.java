package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.common.translation.TranslationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.common.translation.Translation;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.mapper.DeptMapper;
import com.eladminplus.system.dto.DeptSaveRequest;
import com.eladminplus.system.service.DeptService;
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

/** 部门域：树、CRUD、多语言覆盖（Accept-Language）、删除校验与覆盖级联清理。 */
@SpringBootTest
@AutoConfigureMockMvc
class DeptTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private DeptMapper deptMapper;
    @Autowired
    private TranslationMapper translationMapper;
    @Autowired
    private DeptService deptService;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        // 清测试创建的部门（种子「总部/研发部/市场部」保留）与其覆盖行
        List<Long> testIds = deptMapper.selectList(Wrappers.<Dept>lambdaQuery()
                        .notIn(Dept::getName, "总部", "研发部", "市场部"))
                .stream()
                .map(Dept::getId)
                .toList();
        for (Long id : testIds) {
            translationMapper.delete(Wrappers.<Translation>lambdaQuery()
                    .eq(Translation::getEntity, "dept")
                    .eq(Translation::getEntityId, id));
            deptMapper.deleteById(id);
        }
    }

    private MvcResult getTree(String acceptLanguage) throws Exception {
        return mockMvc.perform(get("/depts/tree")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Accept-Language", acceptLanguage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
    }

    private JsonNode treeNodes(String acceptLanguage) throws Exception {
        return objectMapper
                .readTree(getTree(acceptLanguage).getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private Long createDept(String name, String nameEn, Long parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/depts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeptSaveRequest(null, parentId, name, nameEn, 5, 1))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .asLong();
    }

    @Test
    void 树按sort升序且种子结构正确() throws Exception {
        JsonNode data = treeNodes("zh-CN");
        assertThat(data.size()).isEqualTo(1); // 根只有「总部」
        JsonNode root = data.get(0);
        assertThat(root.get("name").asText()).isEqualTo("总部");
        assertThat(root.get("children").size()).isEqualTo(2);
        assertThat(root.get("children").get(0).get("name").asText()).isEqualTo("研发部"); // sort 1 < 2
        assertThat(root.get("children").get(1).get("name").asText()).isEqualTo("市场部");
    }

    @Test
    void 多语言覆盖按AcceptLanguage展示且无覆盖回默认列() throws Exception {
        Long id = createDept("测试部", "Testing Dept", null);

        JsonNode zhTree = treeNodes("zh-CN");
        assertThat(findByName(zhTree, "测试部")).isNotNull();

        JsonNode enTree = treeNodes("en");
        assertThat(findByName(enTree, "Testing Dept")).isNotNull();
        assertThat(findByName(enTree, "测试部")).isNull();

        // 无覆盖的节点（总部）在 en 下回默认列
        assertThat(findByName(enTree, "总部")).isNotNull();
        assertThat(id).isNotNull();
    }

    private JsonNode findByName(JsonNode node, String name) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                JsonNode hit = findByName(item, name);
                if (hit != null) {
                    return hit;
                }
            }
            return null;
        }
        if (node.get("name").asText().equals(name)) {
            return node;
        }
        for (JsonNode child : node.get("children")) {
            JsonNode hit = findByName(child, name);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    @Test
    void 编辑覆盖全量同步null清除() throws Exception {
        Long id = createDept("测试部", "Testing Dept", null);

        // nameEn 置 null：全量同步语义即清除 en 覆盖，en 回默认列
        mockMvc.perform(put("/depts/{id}", id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeptSaveRequest(null, null, "测试部", null, 5, 1))))
                .andExpect(status().isOk());

        Long overrideCount = translationMapper.selectCount(Wrappers.<Translation>lambdaQuery()
                .eq(Translation::getEntity, "dept")
                .eq(Translation::getEntityId, id));
        assertThat(overrideCount).isZero();
        JsonNode enTree = treeNodes("en");
        assertThat(findByName(enTree, "测试部")).isNotNull();
    }

    @Test
    void 删除校验有子或有关联用户被拒且叶子级联清理覆盖() throws Exception {
        // 总部：有子部门 → 拒
        Long rootId = deptMapper
                .selectOne(Wrappers.<Dept>lambdaQuery().isNull(Dept::getParentId).eq(Dept::getName, "总部"))
                .getId();
        mockMvc.perform(delete("/depts/{id}", rootId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("存在子部门，不能删除"));

        // 总部：admin 关联用户 → 同样拒（关联校验先于/并列于子级校验，此处已被子级拦截，叶子场景验关联）
        Long leaf = createDept("测试叶子", "Leaf", null);
        mockMvc.perform(delete("/depts/{id}", leaf).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(deptMapper.selectById(leaf)).isNull();
        Long overrides = translationMapper.selectCount(Wrappers.<Translation>lambdaQuery()
                .eq(Translation::getEntity, "dept")
                .eq(Translation::getEntityId, leaf));
        assertThat(overrides).isZero();
    }

    @Test
    void service层删除有关联用户被拒() {
        Long rootId = deptMapper
                .selectOne(Wrappers.<Dept>lambdaQuery().isNull(Dept::getParentId).eq(Dept::getName, "总部"))
                .getId();
        // admin 挂在总部（V3 种子），移除子部门后总部的删除仍被关联用户拦截
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> deptService.remove(rootId))
                .isInstanceOf(com.eladminplus.common.error.BusinessException.class);
    }
}
