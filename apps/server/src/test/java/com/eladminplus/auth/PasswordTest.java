package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/** 改密：旧密码错误 4xx 明确消息；改密成功后该用户全部会话失效（含当前再访问 401），新密码可登录旧密码拒。 */
@SpringBootTest
@AutoConfigureMockMvc
class PasswordTest extends RefreshLogoutTest {

    private static final String NEW_PASSWORD = "newpass456";

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TokenService tokenService;

    private String adminPasswordHash;

    @BeforeEach
    void snapshotAdmin() {
        adminPasswordHash = userMapper
                .selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"))
                .getPassword();
    }

    @AfterEach
    void restoreAdmin() {
        // 密码与全量会话还原（种子密码 123456 供他测登录链复用）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getUsername, "admin")
                .set(User::getPassword, adminPasswordHash));
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        tokenService.revokeAllForUser(admin.getId());
    }

    private void changePassword(String accessToken, String oldPassword, String newPassword) throws Exception {
        mockMvc
                .perform(put("/auth/me/password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + oldPassword + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andReturn();
    }

    @Test
    void 旧密码错误400且不生效() throws Exception {
        LoginSession session = loginSuccessfully();
        MvcResult result = mockMvc
                .perform(put("/auth/me/password")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"wrong-old\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("旧密码不正确"))
                .andReturn();

        // 拒绝后原密码仍有效、会话未受影响
        mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isOk());
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("旧密码不正确");
    }

    @Test
    void 改密成功全部会话失效且新密码可登录() throws Exception {
        LoginSession session = loginSuccessfully();
        // 第二处会话（另一设备语义）：同样须被强退
        String otherSessionToken = tokenService
                .issue(
                        userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin")),
                        List.of("ROLE_ROOT"),
                        List.of())
                .accessToken();

        mockMvc
                .perform(put("/auth/me/password")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"123456\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 含当前会话在内全部失效：再访问 401
        mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc
                .perform(get("/auth/me").header("Authorization", "Bearer " + otherSessionToken))
                .andExpect(status().isUnauthorized());

        // 旧密码拒、新密码可登录
        MvcResult oldLogin = LoginTestSupport.fullLogin(mockMvc, redis, "admin", "123456");
        assertThat(oldLogin.getResponse().getStatus()).isEqualTo(401);
        MvcResult newLogin = LoginTestSupport.fullLogin(mockMvc, redis, "admin", NEW_PASSWORD);
        assertThat(newLogin.getResponse().getStatus()).isEqualTo(200);
    }
}
