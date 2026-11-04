package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.monitor.dto.ServerMonitorView;
import com.eladminplus.monitor.service.ServerMonitorService;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 服务监控单端点：一次返回六组指标、单项采集失败该组置空其余组正常、权限拦截。 */
@SpringBootTest
@AutoConfigureMockMvc
class ServerMonitorTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MeterRegistry meterRegistry;

    private String accessToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    private ServerMonitorView getServer(String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/monitor/server").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.treeToValue(objectMapper.readTree(body).get("data"), ServerMonitorView.class);
    }

    @Test
    void 单端点一次取齐六组指标() throws Exception {
        ServerMonitorView view = getServer(accessToken());

        assertThat(view.cpu()).isNotNull();
        assertThat(view.cpu().usedPercent()).isBetween(0.0, 100.0);
        assertThat(view.cpu().logicalCores()).isPositive();

        assertThat(view.memory()).isNotNull();
        assertThat(view.memory().totalBytes()).isPositive();
        assertThat(view.memory().usedPercent()).isBetween(0.0, 100.0);

        assertThat(view.swap()).isNotNull();

        assertThat(view.disks()).isNotEmpty();
        assertThat(view.disks().get(0).mount()).isNotBlank();

        assertThat(view.jvm()).isNotNull();
        assertThat(view.jvm().threadCount()).isPositive();
        assertThat(view.jvm().heapUsedBytes()).isPositive();

        // 连接池指标由 actuator 自动绑定（HikariCP + micrometer）
        assertThat(view.pool()).isNotNull();
        assertThat(view.pool().max()).isNotNull();
    }

    @Test
    void 单项采集失败该组置空其余组正常() {
        ServerMonitorService failingCpu = new ServerMonitorService(meterRegistry) {
            @Override
            protected ServerMonitorView.CpuView cpu(oshi.hardware.CentralProcessor processor) {
                throw new IllegalStateException("采集不可得（测试桩）");
            }
        };

        ServerMonitorView view = failingCpu.collect();
        assertThat(view.cpu()).isNull();
        assertThat(view.jvm()).isNotNull();
        assertThat(view.pool()).isNotNull();
        assertThat(view.memory()).isNotNull();
    }

    @Test
    void 无list权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/monitor/server").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
