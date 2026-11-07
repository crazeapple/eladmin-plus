package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * @Log 落库链路：标注端点异步落 operation_log 五字段齐、password 类字段值掩码、
 * 耗时在切面内计时；未标注端点不落行。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OperationLogTest {

    private static final String WRITE_DESCRIPTION = "沙箱写入";

    private static final String SILENT_DESCRIPTION = "静默写入";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        operationLogMapper.delete(Wrappers.<OperationLog>lambdaQuery()
                .in(OperationLog::getDescription, WRITE_DESCRIPTION, SILENT_DESCRIPTION));
    }

    private String accessToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    private void postStub(String path, String token, Map<String, Object> body) throws Exception {
        mockMvc.perform(post(path)
                        .header("Authorization", "Bearer " + token)
                        // MockMvc 不过 RemoteIpValve：对端地址直拟反代解析结果（XFF 收口后头不再被读取）
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    /** 轮询等待异步落表：deadline 内未可见即失败 */
    private List<OperationLog> awaitRows(String description) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        List<OperationLog> rows = List.of();
        while (System.currentTimeMillis() <= deadline) {
            rows = selectByDescription(description);
            if (!rows.isEmpty()) {
                return rows;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待操作日志落表超时：description=" + description);
    }

    /** 观察型轮询（静默断言用）：观察窗口结束后返回所见行集，交由调用方断言 */
    private List<OperationLog> observeRows(String description, long observeMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + observeMillis;
        List<OperationLog> rows = List.of();
        while (System.currentTimeMillis() <= deadline) {
            rows = selectByDescription(description);
            if (!rows.isEmpty()) {
                return rows;
            }
            Thread.sleep(50);
        }
        return rows;
    }

    private List<OperationLog> selectByDescription(String description) {
        return operationLogMapper.selectList(
                Wrappers.<OperationLog>lambdaQuery().eq(OperationLog::getDescription, description));
    }

    @Test
    void 标注端点调用后异步落库且五字段齐() throws Exception {
        String token = accessToken();
        postStub("/test/log/write", token, Map.of("username", "alice", "city", "杭州"));

        List<OperationLog> rows = awaitRows(WRITE_DESCRIPTION);
        OperationLog row = rows.get(0);
        assertThat(row.getUsername()).isEqualTo("admin");
        assertThat(row.getIp()).isEqualTo("203.0.113.9");
        assertThat(row.getDescription()).isEqualTo(WRITE_DESCRIPTION);
        assertThat(row.getElapsedMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(row.getRequestParam())
                .contains("\"username\":\"alice\"")
                .contains("\"city\":\"杭州\"");
    }

    @Test
    void password类字段值掩码入库且嵌套与大小写变体同样生效() throws Exception {
        String token = accessToken();
        postStub("/test/log/write", token, Map.of(
                "password", "plain-secret",
                "loginPassword", "variant-secret",
                "profile", Map.of("nestedPassword", "nested-secret", "city", "杭州"),
                "remark", "保持原样"));

        OperationLog row = awaitRows(WRITE_DESCRIPTION).get(0);
        assertThat(row.getRequestParam())
                .contains("\"password\":\"******\"")
                .contains("\"loginPassword\":\"******\"")
                .contains("\"nestedPassword\":\"******\"")
                .contains("\"remark\":\"保持原样\"")
                .doesNotContain("plain-secret")
                .doesNotContain("variant-secret")
                .doesNotContain("nested-secret");
    }

    @Test
    void 未标注端点调用后不落行() throws Exception {
        String token = accessToken();
        postStub("/test/log/silent", token, Map.of("username", "bob"));

        // 异步旁路若误落，观察窗口内应可见；窗口结束仍无行即通过
        assertThat(observeRows(SILENT_DESCRIPTION, 2_000)).isEmpty();
    }
}
