package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.mapper.ExceptionLogMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** 异常日志钩子：5xx 族异常落 exception_log 含堆栈文本（超长截断），4xx 族业务拒绝不落表。 */
@SpringBootTest
@AutoConfigureMockMvc
class ExceptionLogTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ExceptionLogMapper exceptionLogMapper;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        exceptionLogMapper.delete(
                Wrappers.<ExceptionLog>lambdaQuery().like(ExceptionLog::getMessage, "冒烟-"));
        exceptionLogMapper.delete(
                Wrappers.<ExceptionLog>lambdaQuery().like(ExceptionLog::getMessage, "长消息-"));
        exceptionLogMapper.delete(
                Wrappers.<ExceptionLog>lambdaQuery().like(ExceptionLog::getMessage, "深栈-"));
    }

    private String accessToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 轮询等待异步落表：按消息标记定位，deadline 内未可见即失败 */
    private ExceptionLog awaitRow(String marker) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        List<ExceptionLog> rows = List.of();
        while (System.currentTimeMillis() <= deadline) {
            rows = exceptionLogMapper.selectList(
                    Wrappers.<ExceptionLog>lambdaQuery().like(ExceptionLog::getMessage, marker));
            if (!rows.isEmpty()) {
                return rows.get(0);
            }
            Thread.sleep(50);
        }
        throw new AssertionError("等待异常日志落表超时：marker=" + marker);
    }

    private List<ExceptionLog> selectByName(String name) {
        return exceptionLogMapper
                .selectList(Wrappers.<ExceptionLog>lambdaQuery().eq(ExceptionLog::getName, name));
    }

    @Test
    void 五百族异常落表含操作人与ip与堆栈文本() throws Exception {
        String marker = UUID.randomUUID().toString();
        mockMvc.perform(get("/test/log/boom").param("marker", marker)
                        .header("Authorization", "Bearer " + accessToken())
                        // MockMvc 不过 RemoteIpValve：对端地址直拟反代解析结果（XFF 收口后头不再被读取）
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.7");
                            return request;
                        }))
                .andExpect(status().isInternalServerError());

        ExceptionLog row = awaitRow("冒烟-" + marker);
        assertThat(row.getName()).isEqualTo(IllegalStateException.class.getName());
        assertThat(row.getMessage()).contains(marker);
        assertThat(row.getUsername()).isEqualTo("admin");
        assertThat(row.getIp()).isEqualTo("203.0.113.7");
        assertThat(row.getStackTrace())
                .contains(IllegalStateException.class.getName())
                .contains("com.eladminplus")
                .contains("\tat ");
    }

    @Test
    void 超长消息与超长堆栈截断入库() throws Exception {
        String marker = UUID.randomUUID().toString();
        mockMvc.perform(get("/test/log/long-message").param("marker", marker))
                .andExpect(status().isInternalServerError());
        mockMvc.perform(get("/test/log/deep-stack").param("marker", marker))
                .andExpect(status().isInternalServerError());

        List<ExceptionLog> rows = List.of();
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() <= deadline) {
            rows = exceptionLogMapper.selectList(Wrappers.<ExceptionLog>lambdaQuery()
                    .like(ExceptionLog::getMessage, "长消息-" + marker)
                    .or()
                    .like(ExceptionLog::getStackTrace, "深栈-" + marker));
            if (rows.size() >= 2) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(rows).hasSize(2);
        ExceptionLog longMessage = rows.stream()
                .filter(row -> row.getMessage().startsWith("长消息-" + marker))
                .findFirst()
                .orElseThrow();
        assertThat(longMessage.getMessage().length()).isLessThanOrEqualTo(512);
        ExceptionLog deepStack = rows.stream()
                .filter(row -> row.getStackTrace() != null && row.getStackTrace().contains("深栈-" + marker))
                .findFirst()
                .orElseThrow();
        assertThat(deepStack.getStackTrace().length()).isLessThanOrEqualTo(20_000);
        // 触底抛出的帧在截断前部完整保留
        assertThat(deepStack.getStackTrace()).contains("recurse");
    }

    @Test
    void 四百族业务拒绝不落表() throws Exception {
        mockMvc.perform(get("/test/boom")).andExpect(status().isBadRequest());

        // 异步旁路若误落，观察窗口内应可见；窗口结束仍无行即通过
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() <= deadline) {
            if (!selectByName(BusinessException.class.getName()).isEmpty()) {
                break;
            }
            Thread.sleep(50);
        }
        assertThat(selectByName(BusinessException.class.getName())).isEmpty();
    }
}
