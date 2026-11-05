package com.eladminplus.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 邮件真实发送：内嵌 SMTP（GreenMail 随机端口）收信断言收件人、主题、正文；HTML 正文可用。 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailSendTest {

    /** 内嵌 SMTP：类加载即起（早于上下文装配读取端口），端口 0 随机分配 */
    private static final GreenMail GREEN_MAIL = new GreenMail(new ServerSetup(0, "127.0.0.1", "smtp"));

    static {
        GREEN_MAIL.start();
    }

    @AfterAll
    static void stopSmtp() {
        GREEN_MAIL.stop();
    }

    @org.springframework.test.context.DynamicPropertySource
    static void mailProps(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", () -> String.valueOf(GREEN_MAIL.getSmtp().getPort()));
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;

    private String rootToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    private String send(String body) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/tools/email/send")
                        .header("Authorization", "Bearer " + rootToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 真实发送收件人主题正文到达() throws Exception {
        send("{\"to\":\"receiver@example.com\",\"subject\":\"季度报表\",\"content\":\"正文内容\",\"html\":false}");

        // 收信箱跨测共享，按收件人锚定本测的信（GreenMail reset 会换随机端口，不可用）
        MimeMessage received = messageTo("receiver@example.com");
        assertThat(received.getSubject()).isEqualTo("季度报表");
        assertThat(received.getDataHandler().getContentType()).contains("text/plain");
        assertThat(textOf(received)).contains("正文内容");
    }

    @Test
    void html正文按html发送() throws Exception {
        send("{\"to\":\"html-receiver@example.com\",\"subject\":\"HTML 信\",\"content\":\"<p>富文本正文</p>\",\"html\":true}");

        MimeMessage received = messageTo("html-receiver@example.com");
        assertThat(received.getDataHandler().getContentType()).contains("text/html");
        assertThat(textOf(received)).contains("富文本正文");
    }

    /** 按收件人取信：收信箱跨测共享（共享 SMTP 实例），以收件人定位断言锚点 */
    private static MimeMessage messageTo(String recipient) {
        try {
            for (MimeMessage message : GREEN_MAIL.getReceivedMessages()) {
                if (List.of(message.getAllRecipients()).stream()
                        .anyMatch(address -> address.toString().equals(recipient))) {
                    return message;
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        throw new AssertionError("收件信未到达：" + recipient);
    }

    /** 单部件文本读取（multipart 兼容取正文部件）；字符集随消息声明解码 */
    private static String textOf(MimeMessage message) {
        try {
            Object content = message.getContent();
            if (content instanceof Multipart multipart) {
                BodyPart part = multipart.getBodyPart(0);
                return (String) part.getContent();
            }
            return (String) content;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
