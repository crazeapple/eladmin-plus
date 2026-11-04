package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.task.TaskMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 任务目标白名单发现：@TaskMethod 清单收编、签名约定过滤、参数说明下发、端点权限拦截。 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskWhitelistTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    /** 测试桩任务 Bean：覆盖签名约定四形态（带参标记 / 无参标记 / 未标记 / 签名不符标记） */
    @TestConfiguration
    static class StubTasks {

        @Bean
        StubTask stubTask() {
            return new StubTask();
        }
    }

    static class StubTask {

        @TaskMethod("样例任务参数说明")
        public void sample(String param) {
            // 白名单扫描桩：不执行任何业务
        }

        @TaskMethod("无参样例")
        public void noArg() {
            // 白名单扫描桩：不执行任何业务
        }

        public void notMarked() {
            // 未标记方法：不进清单
        }

        @TaskMethod("签名不符")
        public void wrongSignature(int count) {
            // 签名约定外标记：不进清单
        }
    }

    private JsonNode targets() throws Exception {
        MvcResult result = mockMvc
                .perform(get("/tasks/targets").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(body).get("data");
    }

    private JsonNode findTarget(JsonNode array, String beanName, String methodName) {
        for (JsonNode item : array) {
            if (item.get("beanName").asText().equals(beanName)
                    && item.get("methodName").asText().equals(methodName)) {
                return item;
            }
        }
        return null;
    }

    @Test
    void 标记方法进清单且参数说明正确() throws Exception {
        JsonNode targets = targets();
        JsonNode sample = findTarget(targets, "stubTask", "sample");
        assertThat(sample).isNotNull();
        assertThat(sample.get("param").asText()).isEqualTo("样例任务参数说明");
        JsonNode noArg = findTarget(targets, "stubTask", "noArg");
        assertThat(noArg).isNotNull();
        assertThat(noArg.get("param").asText()).isEqualTo("无参样例");
    }

    @Test
    void 未标记与签名不符方法不出现在清单() throws Exception {
        JsonNode targets = targets();
        assertThat(findTarget(targets, "stubTask", "notMarked")).isNull();
        assertThat(findTarget(targets, "stubTask", "wrongSignature")).isNull();
    }

    @Test
    void 演示任务Bean在册清单非空() throws Exception {
        JsonNode targets = targets();
        // 演示 Bean 保证生产白名单非空（任务表单下拉与主链路前置）
        JsonNode sampleJob = findTarget(targets, "sampleJob", "run");
        assertThat(sampleJob).isNotNull();
        assertThat(sampleJob.get("param").asText()).isEqualTo("演示参数，原样打印到日志");
    }

    @Test
    void 无list权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/tasks/targets").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
