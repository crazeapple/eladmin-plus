package com.eladminplus.tools;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 邮件工具（SMTP 未配置的缺省形态）：发送端点报「邮件服务未启用」；收件人非法 422；无权限 403。 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    @Test
    void 未配置SMTP报邮件服务未启用() throws Exception {
        mockMvc
                .perform(post("/tools/email/send")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"someone@example.com\",\"subject\":\"hi\",\"content\":\"hello\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("邮件服务未启用"));
    }

    @Test
    void 收件人格式非法422() throws Exception {
        mockMvc
                .perform(post("/tools/email/send")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"not-an-email\",\"subject\":\"hi\",\"content\":\"hello\"}"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.message").value("收件人邮箱格式不合法"));
    }

    @Test
    void 无send权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc
                .perform(post("/tools/email/send")
                        .header("Authorization", "Bearer " + plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"someone@example.com\",\"subject\":\"hi\",\"content\":\"hello\"}"))
                .andExpect(status().isForbidden());
    }
}
