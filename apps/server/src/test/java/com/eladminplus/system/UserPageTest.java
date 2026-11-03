package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.Dept;
import com.eladminplus.system.mapper.DeptMapper;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 用户分页查询：page 0 起、部门含子级过滤、模糊、状态与时间筛选。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserPageTest {

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

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    private JsonNode page(String qs) throws Exception {
        MvcResult result = mockMvc.perform(get("/users" + (qs.isEmpty() ? "" : "?" + qs))
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .get("data");
    }

    private Long deptId(String name) {
        return deptMapper.selectList(Wrappers.<Dept>lambdaQuery().eq(Dept::getName, name)).stream()
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @Test
    void 分页零起且含字段与关联视图() throws Exception {
        JsonNode data = page("");
        assertThat(data.get("page").asInt()).isZero();
        assertThat(data.get("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode admin = data.get("content").get(0);
        assertThat(admin.get("username").asText()).isEqualTo("admin");
        assertThat(admin.get("deptName").asText()).isEqualTo("总部");
        assertThat(admin.get("roleIds").size()).isGreaterThanOrEqualTo(1);
        assertThat(admin.get("enabled").asInt()).isEqualTo(1);
    }

    @Test
    void 部门过滤含子级() throws Exception {
        // admin 挂总部 → 按研发部（子级）过滤不含 admin；按总部过滤含 admin
        Long rd = deptId("研发部");
        assertThat(page("deptId=" + rd).get("total").asLong()).isZero();
        Long hq = deptId("总部");
        assertThat(page("deptId=" + hq).get("total").asLong()).isEqualTo(1);
    }

    @Test
    void 名称与状态与时间筛选() throws Exception {
        assertThat(page("name=admin").get("total").asLong()).isEqualTo(1);
        assertThat(page("name=不存在").get("total").asLong()).isZero();
        assertThat(page("enabled=1").get("total").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(page("enabled=0").get("total").asLong()).isZero();
        // 未来时间范围：不含种子用户
        assertThat(page("startTime=2099-01-01T00:00:00").get("total").asLong()).isZero();
    }
}
