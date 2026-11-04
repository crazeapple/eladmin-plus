package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 操作日志维护端点：分页筛选（操作人 / IP / 描述模糊 / 时间范围）、单删批删清空、Excel 导出与权限拦截。 */
@SpringBootTest
@AutoConfigureMockMvc
class OperationLogMaintenanceTest {

    /** 测试行用户名前缀，清理定位用 */
    private static final String TEST_PREFIX = "logtest.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private OperationLogMapper operationLogMapper;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanBefore() {
        cleanTestRows();
    }

    @AfterEach
    void cleanAfter() {
        cleanTestRows();
    }

    private void cleanTestRows() {
        operationLogMapper.delete(
                Wrappers.<OperationLog>lambdaQuery().likeRight(OperationLog::getUsername, TEST_PREFIX));
    }

    private String accessToken() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        return tokenService.issue(admin, List.of("ROLE_ROOT"), List.of()).accessToken();
    }

    /** 插入测试行（时间列直接落库以测时间范围筛选），返回行 id */
    private Long insertRow(String username, String ip, String description, String createdAt) {
        OperationLog row = new OperationLog();
        row.setUsername(username);
        row.setIp(ip);
        row.setDescription(description);
        row.setElapsedMs(12L);
        row.setRequestParam("{}");
        operationLogMapper.insert(row);
        if (createdAt != null) {
            jdbc.update("UPDATE operation_log SET created_at = ? WHERE id = ?", createdAt, row.getId());
        }
        return row.getId();
    }

    private String listBody(String queryString, String token) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/logs/operations" + queryString).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    void 分页与新记录在前() throws Exception {
        String token = accessToken();
        insertRow(TEST_PREFIX + "u1", "10.0.0.1", "登录操作", null);
        Long lastId = insertRow(TEST_PREFIX + "u2", "10.0.0.2", "登出操作", null);

        String body = listBody("?page=0&size=10", token);
        assertThat(body).contains("\"total\":" + (operationLogMapper.selectCount(Wrappers.emptyWrapper())));
        // 新记录在前：u2 行先于 u1 行出现
        assertThat(body.indexOf(TEST_PREFIX + "u2")).isLessThan(body.indexOf(TEST_PREFIX + "u1"));
        assertThat(body).contains("\"id\":" + lastId);
    }

    @Test
    void 操作人与ip精确筛选生效() throws Exception {
        String token = accessToken();
        insertRow(TEST_PREFIX + "ua", "10.0.0.1", "发布操作", null);
        insertRow(TEST_PREFIX + "ub", "10.0.0.2", "发布操作", null);

        String body = listBody("?username=" + TEST_PREFIX + "ua", token);
        assertThat(body).contains(TEST_PREFIX + "ua");
        assertThat(body).doesNotContain(TEST_PREFIX + "ub");

        String ipBody = listBody("?ip=10.0.0.2", token);
        assertThat(ipBody).contains(TEST_PREFIX + "ub");
        assertThat(ipBody).doesNotContain(TEST_PREFIX + "ua");
    }

    @Test
    void 描述模糊与时间范围筛选生效() throws Exception {
        String token = accessToken();
        insertRow(TEST_PREFIX + "u1", "10.0.0.1", "任务重跑操作", null);
        insertRow(TEST_PREFIX + "u2", "10.0.0.1", "任务暂停操作", null);
        insertRow(TEST_PREFIX + "u3", "10.0.0.1", "任务重跑操作", "2026-01-01 08:00:00");

        String likeBody = listBody("?description=重跑", token);
        assertThat(likeBody).contains(TEST_PREFIX + "u1");
        assertThat(likeBody).doesNotContain(TEST_PREFIX + "u2");

        String rangeBody = listBody("?startTime=2025-12-31T00:00:00&endTime=2026-01-01T23:59:59", token);
        assertThat(rangeBody).contains(TEST_PREFIX + "u3");
        assertThat(rangeBody).doesNotContain(TEST_PREFIX + "u1");

        mockMvc.perform(get("/logs/operations").param("startTime", "not-a-date")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 单删批删与清空生效() throws Exception {
        String token = accessToken();
        Long keepId = insertRow(TEST_PREFIX + "keep", "10.0.0.1", "保留行", null);
        Long singleId = insertRow(TEST_PREFIX + "single", "10.0.0.1", "单删行", null);
        Long batchId = insertRow(TEST_PREFIX + "batch", "10.0.0.1", "批删行", null);

        mockMvc.perform(delete("/logs/operations/{id}", singleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(operationLogMapper.selectById(singleId)).isNull();
        assertThat(operationLogMapper.selectById(keepId)).isNotNull();

        mockMvc.perform(delete("/logs/operations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[" + batchId + "]"))
                .andExpect(status().isOk());
        assertThat(operationLogMapper.selectById(batchId)).isNull();

        // 不存在行按 404 拒绝
        mockMvc.perform(delete("/logs/operations/{id}", singleId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/logs/operations/all").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(operationLogMapper.selectCount(Wrappers.emptyWrapper())).isZero();
    }

    @Test
    void 导出当前筛选全集且行集一致() throws Exception {
        String token = accessToken();
        insertRow(TEST_PREFIX + "ua", "10.0.0.1", "导出命中行", null);
        insertRow(TEST_PREFIX + "ub", "10.0.0.2", "导出排除行", null);

        MvcResult result = mockMvc
                .perform(get("/logs/operations/export")
                        .param("username", TEST_PREFIX + "ua")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(result.getResponse().getHeader("Content-Disposition")).contains("operation_log.xlsx");
        // xlsx 即 zip 容器，魔数在册
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');

        List<Map<Integer, String>> rows =
                EasyExcel.read(new ByteArrayInputStream(bytes)).sheet().doReadSync();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).values()).contains(TEST_PREFIX + "ua", "导出命中行");
    }

    @Test
    void 无list与del权限403() throws Exception {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        String plainToken = tokenService.issue(admin, List.of(), List.of()).accessToken();
        mockMvc.perform(get("/logs/operations").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/logs/operations/all").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/logs/operations/export").header("Authorization", "Bearer " + plainToken))
                .andExpect(status().isForbidden());
    }
}
