package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.LoginTestSupport;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.eladminplus.system.dto.UserSaveRequest;
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
import tools.jackson.databind.ObjectMapper;

/** 状态启停：停用即强退（旧 access 即失效）、停用登录统一拒绝、保护语义、启用恢复。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserEnableTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private StringRedisTemplate redis;

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
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "enable-test"));
        if (user != null) {
            userMapper.deleteById(user.getId());
        }
    }

    private Long createEnabledUser() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserSaveRequest(
                                null, "enable-test", null, "enable-test@test.local", null, 1, 1, null, "123456",
                                List.of(), List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private String loginOf(String username) throws Exception {
        MvcResult result = LoginTestSupport.fullLogin(mockMvc, redis, username, "123456");
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 停用即强退且登录拒绝启用后恢复() throws Exception {
        Long userId = createEnabledUser();
        // 该用户登录拿 access（登录链含验证码与 RSA——直接用令牌服务签发等价会话,验证链在 P-009 已覆盖）
        User target = userMapper.selectById(userId);
        String userToken = tokenService.issue(target, List.of(), List.of()).accessToken();

        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden()); // 非权限点用户:鉴权拒绝但会话有效(403 非 401)

        // admin 停用
        mockMvc.perform(put("/users/{id}/enabled/{value}", userId, 0)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // 旧 access 即失效(401)
        mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // 停用后登录统一拒绝（与密码错误同文案）
        String body = loginOf("enable-test");
        assertThat(objectMapper.readTree(body).get("code").asInt()).isEqualTo(401);
        assertThat(objectMapper.readTree(body).get("message").asText()).isEqualTo("账号或密码错误");

        // 启用后可登录
        mockMvc.perform(put("/users/{id}/enabled/{value}", userId, 1)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        String relogin = loginOf("enable-test");
        assertThat(objectMapper.readTree(relogin).get("code").asInt()).isEqualTo(200);
    }

    @Test
    void 不能停用自己且admin受停用保护() throws Exception {
        mockMvc.perform(put("/users/{id}/enabled/{value}", adminId, 0)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("不能禁用自己"));
        // admin 账号即操作者本人,两语义重叠由自我保护覆盖;此处直接断言不存在第二个入口停用 admin:
        // 新建低权用户试图停用 admin → admin 保护(该用户无权限点则 403,先到先拦,保护语义仍在)
        Long userId = createEnabledUser();
        User other = userMapper.selectById(userId);
        String otherToken = tokenService.issue(other, List.of(), List.of("system:user:edit")).accessToken();
        mockMvc.perform(put("/users/{id}/enabled/{value}", adminId, 0)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("admin 账号受保护，不能删除或禁用"));
    }
}
