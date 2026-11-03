package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** 重置密码：批量重置为默认密码、旧密码失效、新默认可登录、既有会话强退。 */
@SpringBootTest
@AutoConfigureMockMvc
class UserResetPasswordTest {

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

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "reset-test"));
        if (user != null) {
            userMapper.deleteById(user.getId());
        }
    }

    @Test
    void 批量重置后旧密码失效新默认可登录且会话被强退() throws Exception {
        MvcResult result = mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UserSaveRequest(
                                null, "reset-test", null, "reset-test@test.local", null, 1, 1, null, "old-pass-9",
                                List.of(), List.of()))))
                .andExpect(status().isOk())
                .andReturn();
        Long userId = objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();

        // 用户以初始密码登录拿会话
        User user = userMapper.selectById(userId);
        String oldToken = tokenService.issue(user, List.of(), List.of()).accessToken();

        // 批量重置
        mockMvc.perform(put("/users/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(userId))))
                .andExpect(status().isOk());

        // 既有会话被强退
        MvcResult after = mockMvc.perform(get("/users")
                        .header("Authorization", "Bearer " + oldToken))
                .andReturn();
        System.out.println("DEBUG-RESET status=" + after.getResponse().getStatus()
                + " body=" + after.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(after.getResponse().getStatus()).isEqualTo(401);

        // 旧密码失效
        MvcResult oldLogin = LoginTestSupport.fullLogin(mockMvc, redis, "reset-test", "old-pass-9");
        assertThat(objectMapper.readTree(oldLogin.getResponse().getContentAsString()).get("code").asInt())
                .isEqualTo(401);

        // 新默认密码可登录
        MvcResult newLogin = LoginTestSupport.fullLogin(mockMvc, redis, "reset-test", "123456");
        assertThat(objectMapper.readTree(newLogin.getResponse().getContentAsString()).get("code").asInt())
                .isEqualTo(200);
    }
}
