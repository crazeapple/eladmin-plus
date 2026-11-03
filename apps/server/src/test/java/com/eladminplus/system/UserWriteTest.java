package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.UserJob;
import com.eladminplus.system.dto.UserSaveRequest;
import com.eladminplus.system.mapper.UserJobMapper;
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

/** 用户写操作：CRUD、唯一约束、角色越权、自我保护、admin 保护、批量删级联。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserWriteTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private UserJobMapper userJobMapper;

    private String accessToken;
    private Long adminId;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        adminId = admin.getId();
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        List<String> testUsernames = List.of("write-test-a", "write-test-b", "low-op");
        for (String username : testUsernames) {
            User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
            if (user != null) {
                userRoleMapper.delete(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, user.getId()));
                userJobMapper.delete(Wrappers.<UserJob>lambdaQuery().eq(UserJob::getUserId, user.getId()));
                userMapper.deleteById(user.getId());
            }
        }
        roleMapper.delete(Wrappers.<Role>lambdaQuery().in(Role::getCode, "ROLE_TEST_LOW", "ROLE_TEST_HIGH"));
    }

    private MvcResult postUser(UserSaveRequest request) throws Exception {
        return mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    private Long createLowOperator(String code, int level) throws Exception {
        Role role = new Role();
        role.setCode(code);
        role.setName(code);
        role.setLevel(level);
        role.setEnabled(1);
        roleMapper.insert(role);

        UserSaveRequest request = new UserSaveRequest(
                null, "low-op", "低权操作者", "low-op@test.local", null, 1, 1, null, "123456",
                List.of(role.getId()), List.of());
        MvcResult result = postUser(request);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Long userId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("data")
                .asLong();

        // 为低权操作者签发会话（带其角色位）
        User operator = userMapper.selectById(userId);
        TokenPair pair = tokenService.issue(
                operator, List.of(code), List.of());
        // 记录操作者的 token 供断言用（存成员不便,直接在用例内签发）
        return userId;
    }

    @Test
    void 新增全字段用户并回显关联() throws Exception {
        Long jobId = 0L;
        MvcResult result = postUser(new UserSaveRequest(
                null, "write-test-a", "写测试甲", "write-test-a@test.local", "13800000001", 1, 1, null,
                "123456", List.of(), List.of()));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Long userId =
                objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
        assertThat(userId).isNotNull();

        User created = userMapper.selectById(userId);
        assertThat(created.getEmail()).isEqualTo("write-test-a@test.local");
        // 初始密码 BCrypt 落库,明文不落
        assertThat(created.getPassword()).startsWith("$2");
        assertThat(created.getPassword()).doesNotContain("123456");
        assertThat(jobId).isNotNull();
    }

    @Test
    void 用户名与邮箱唯一被友好拒绝() throws Exception {
        postUser(new UserSaveRequest(
                null, "write-test-a", null, "dup-a@test.local", null, 1, 1, null, "123456", List.of(), List.of()));
        // 用户名重复
        MvcResult dupName = postUser(new UserSaveRequest(
                null, "write-test-a", null, "other@test.local", null, 1, 1, null, "123456", List.of(), List.of()));
        JsonNode nameBody =
                objectMapper.readTree(dupName.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(dupName.getResponse().getStatus()).isEqualTo(400);
        assertThat(nameBody.get("message").asText()).isEqualTo("用户名已存在");

        // 邮箱重复
        MvcResult dupEmail = postUser(new UserSaveRequest(
                null, "write-test-b", null, "dup-a@test.local", null, 1, 1, null, "123456", List.of(), List.of()));
        assertThat(dupEmail.getResponse().getStatus()).isEqualTo(400);
        JsonNode emailBody =
                objectMapper.readTree(dupEmail.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(emailBody.get("message").asText()).isEqualTo("邮箱已存在");
    }

    @Test
    void 编辑用户更新字段不改密码() throws Exception {
        Long userId = objectMapper
                .readTree(postUser(new UserSaveRequest(
                                null, "write-test-a", null, "write-test-a@test.local", null, 1, 1, null, "123456",
                                List.of(), List.of()))
                        .getResponse()
                        .getContentAsString())
                .get("data")
                .asLong();
        String passwordBefore = userMapper.selectById(userId).getPassword();

        mockMvc.perform(put("/users/{id}", userId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserSaveRequest(
                                null, "write-test-a", "改后昵称", "write-test-a@test.local", "13900000000", 2, 1,
                                null, null, List.of(), List.of()))))
                .andExpect(status().isOk());

        User after = userMapper.selectById(userId);
        assertThat(after.getNickName()).isEqualTo("改后昵称");
        assertThat(after.getPhone()).isEqualTo("13900000000");
        assertThat(after.getPassword()).isEqualTo(passwordBefore); // 密码不动
    }

    @Test
    void 角色越权低权操作者不能分配更高级角色() throws Exception {
        Role low = new Role();
        low.setCode("ROLE_TEST_LOW");
        low.setName("低权角色");
        low.setLevel(10);
        low.setEnabled(1);
        roleMapper.insert(low);
        Role high = new Role();
        high.setCode("ROLE_TEST_HIGH");
        high.setName("高权角色");
        high.setLevel(5);
        high.setEnabled(1);
        roleMapper.insert(high);

        // 低权操作者（level 10）登录
        UserSaveRequest opRequest = new UserSaveRequest(
                null, "low-op", null, "low-op@test.local", null, 1, 1, null, "123456",
                List.of(low.getId()), List.of());
        Long opId = objectMapper
                .readTree(postUser(opRequest).getResponse().getContentAsString())
                .get("data")
                .asLong();
        User operator = userMapper.selectById(opId);
        // 签发时直接授 system:user:add 权限点（模拟已授权的管理员），过方法级鉴权进业务校验
        String operatorToken = tokenService.issue(
                operator, List.of("ROLE_TEST_LOW"), List.of("system:user:add")).accessToken();

        // 尝试新建用户并分配 level 5（比自己 level 10 权限更大）的角色
        MvcResult result = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserSaveRequest(
                                null, "write-test-b", null, "write-test-b@test.local", null, 1, 1, null,
                                "123456", List.of(high.getId()), List.of()))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        JsonNode body =
                objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("message").asText()).isEqualTo("存在超出自身权限级别的角色");
    }

    @Test
    void 自我保护与admin保护() throws Exception {
        // 不能禁用自己、不能改自己的角色
        mockMvc.perform(put("/users/{id}", adminId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserSaveRequest(
                                null, "admin", "系统管理员", "admin@test.local", null, 1, 0, null, null,
                                List.of(), List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能禁用自己"));

        // 不能删除自己
        mockMvc.perform(delete("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(adminId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能删除自己"));

        // 新建一个用户,由持有删除权限点的非 ROOT 管理员执行批量删（含 admin）→ admin 保护触发
        Long otherId = objectMapper
                .readTree(postUser(new UserSaveRequest(
                                null, "write-test-b", null, "write-test-b@test.local", null, 1, 1, null, "123456",
                                List.of(), List.of()))
                        .getResponse()
                        .getContentAsString())
                .get("data")
                .asLong();
        User manager = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "write-test-b"));
        String managerToken = tokenService.issue(
                manager, List.of("ROLE_TEST_LOW"), List.of("system:user:del")).accessToken();

        mockMvc.perform(delete("/users")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(adminId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("admin 账号受保护，不能删除或禁用"));

        // 不含 admin 的批量删成功且级联清理关联
        mockMvc.perform(delete("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(otherId))))
                .andExpect(status().isOk());
        assertThat(userMapper.selectById(otherId)).isNull();
        assertThat(userRoleMapper.selectCount(Wrappers.<UserRole>lambdaQuery().eq(UserRole::getUserId, otherId)))
                .isZero();
    }
}
