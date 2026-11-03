package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.Task;
import com.eladminplus.system.domain.TaskLog;
import com.eladminplus.system.dto.TaskSaveRequest;
import com.eladminplus.system.mapper.TaskLogMapper;
import com.eladminplus.system.mapper.TaskMapper;
import com.eladminplus.system.service.TaskService;
import com.eladminplus.system.task.TaskMethod;
import java.util.List;
import java.util.Properties;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 任务操作与执行日志：暂停 / 恢复触发态翻转、立即执行异步落日志、同库重启恢复、日志分页过滤。 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskLifecycleTest {

    /** 测试任务名前缀，清理定位用 */
    private static final String TEST_NAME_PREFIX = "testlife.";

    private static final long AWAIT_TIMEOUT_MS = 15000;
    private static final long AWAIT_INTERVAL_MS = 200;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskLogMapper taskLogMapper;
    @Autowired
    private Scheduler scheduler;
    @Autowired
    private javax.sql.DataSource dataSource;

    private String accessToken;

    @BeforeEach
    void signInRoot() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        TokenPair pair = tokenService.issue(admin, List.of("ROLE_ROOT"), List.of());
        accessToken = pair.accessToken();
    }

    @AfterEach
    void cleanUp() {
        List<Long> testIds = taskMapper
                .selectList(Wrappers.<Task>lambdaQuery().likeRight(Task::getName, TEST_NAME_PREFIX))
                .stream()
                .map(Task::getId)
                .toList();
        for (Long id : testIds) {
            try {
                scheduler.deleteJob(TaskService.jobKeyOf(id));
            } catch (org.quartz.SchedulerException e) {
                // 清理尽力而为：调度删除失败不阻断行清理
            }
            taskLogMapper.delete(Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getTaskId, id));
            taskMapper.deleteById(id);
        }
    }

    /** 测试桩任务 Bean：成功样例带短暂耗时，失败样例抛带标记的异常 */
    @TestConfiguration
    static class StubTasks {

        @Bean
        StubTask stubTask() {
            return new StubTask();
        }
    }

    /** 公开类：Quartz 执行壳跨包反射 invoke 要求类可访问 */
    public static class StubTask {

        @TaskMethod("样例参数")
        public void sample(String param) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @TaskMethod("失败样例")
        public void fail() {
            throw new IllegalStateException("样例执行失败-boom");
        }
    }

    private Long createTask(String name, String methodName, String param) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskSaveRequest(
                                null, name, "stubTask", methodName, param, "0 0 6 * * ?", 1))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private void postAction(String action, Long id) throws Exception {
        mockMvc.perform(post("/tasks/{id}/{action}", id, action).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private JsonNode logsOf(Long taskId, int page, int size) throws Exception {
        MvcResult result = mockMvc
                .perform(get("/tasks/{id}/logs", taskId)
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data");
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(AWAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(condition.getAsBoolean()).as("等待条件在超时内未成立").isTrue();
    }

    @Test
    void 暂停恢复翻转触发状态() throws Exception {
        Long id = createTask(TEST_NAME_PREFIX + "pause", "sample", null);
        TriggerKey triggerKey = TaskService.triggerKeyOf(id);

        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(org.quartz.Trigger.TriggerState.NORMAL);
        postAction("pause", id);
        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(org.quartz.Trigger.TriggerState.PAUSED);
        postAction("resume", id);
        assertThat(scheduler.getTriggerState(triggerKey)).isEqualTo(org.quartz.Trigger.TriggerState.NORMAL);
    }

    @Test
    void 立即执行异步落一条成功日志() throws Exception {
        Long id = createTask(TEST_NAME_PREFIX + "exec", "sample", "run-once");
        postAction("exec", id);

        await(() -> taskLogMapper
                        .selectCount(Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getTaskId, id))
                == 1);
        TaskLog entry = taskLogMapper
                .selectList(Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getTaskId, id))
                .get(0);
        assertThat(entry.getSuccess()).isEqualTo(1);
        assertThat(entry.getExecuteTime()).isNotNull();
        assertThat(entry.getDurationMs()).isNotNull();
        assertThat(entry.getErrorMsg()).isNull();
    }

    @Test
    void 失败执行日志含异常摘要() throws Exception {
        Long id = createTask(TEST_NAME_PREFIX + "fail", "fail", null);
        postAction("exec", id);

        await(() -> taskLogMapper
                        .selectCount(Wrappers.<TaskLog>lambdaQuery()
                                .eq(TaskLog::getTaskId, id)
                                .eq(TaskLog::getSuccess, 0))
                >= 1);
        TaskLog entry = taskLogMapper
                .selectList(Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getTaskId, id))
                .get(0);
        assertThat(entry.getSuccess()).isZero();
        assertThat(entry.getErrorMsg()).contains("样例执行失败-boom");
    }

    @Test
    void 同库重启任务定义与暂停态恢复() throws Exception {
        Long id = createTask(TEST_NAME_PREFIX + "restart", "sample", null);
        postAction("pause", id);

        // 同库重建 scheduler（模拟重启）：同实例名、同 JDBC store，读侧校验不触发调度
        Scheduler probe = probeScheduler();
        try {
            JobKey jobKey = TaskService.jobKeyOf(id);
            JobDetail jobDetail = probe.getJobDetail(jobKey);
            assertThat(jobDetail).isNotNull();
            assertThat(jobDetail.getJobDataMap().getString("methodName")).isEqualTo("sample");
            assertThat(probe.getTriggerState(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup())))
                    .isEqualTo(org.quartz.Trigger.TriggerState.PAUSED);
        } finally {
            probe.shutdown(false);
        }
    }

    @Test
    void 日志分页按任务过滤各取各的() throws Exception {
        Long idA = createTask(TEST_NAME_PREFIX + "log-a", "sample", "a");
        Long idB = createTask(TEST_NAME_PREFIX + "log-b", "sample", "b");
        postAction("exec", idA);
        postAction("exec", idB);

        await(() -> logsCount(idA) >= 1 && logsCount(idB) >= 1);

        JsonNode pageA = logsOf(idA, 0, 10);
        assertThat(pageA.get("total").asLong()).isEqualTo(logsCount(idA));
        for (JsonNode entry : pageA.get("content")) {
            assertThat(entry.get("taskId").asLong()).isEqualTo(idA);
        }

        JsonNode pageB = logsOf(idB, 0, 10);
        assertThat(pageB.get("total").asLong()).isEqualTo(logsCount(idB));
        for (JsonNode entry : pageB.get("content")) {
            assertThat(entry.get("taskId").asLong()).isEqualTo(idB);
        }

        // 分页切片：size 1 只取一条
        JsonNode slice = logsOf(idA, 0, 1);
        assertThat(slice.get("content").size()).isEqualTo(1);
        assertThat(slice.get("size").asInt()).isEqualTo(1);
    }

    private long logsCount(Long taskId) {
        return taskLogMapper.selectCount(Wrappers.<TaskLog>lambdaQuery().eq(TaskLog::getTaskId, taskId));
    }

    /** 同库重建 scheduler：实例名沿用运行中的，连接复用当前数据源（自定义 ConnectionProvider 静态持有） */
    private Scheduler probeScheduler() throws Exception {
        probeSource = dataSource;
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", scheduler.getSchedulerName());
        props.setProperty("org.quartz.scheduler.instanceId", "probe-restart-check");
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", "1");
        props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
        props.setProperty("org.quartz.jobStore.dataSource", "probeDs");
        props.setProperty(
                "org.quartz.dataSource.probeDs.connectionProvider.class",
                "com.eladminplus.system.TaskLifecycleTest$HeldSourceProvider");
        return new StdSchedulerFactory(props).getScheduler();
    }

    /** probe scheduler 的连接来源（静态持有避免 quartz 反射构造传参） */
    private static volatile javax.sql.DataSource probeSource;

    /** 复用 Spring 数据源的 quartz 连接提供者：只读校验用，不归 quartz 生命周期管 */
    public static class HeldSourceProvider implements org.quartz.utils.ConnectionProvider {

        @Override
        public java.sql.Connection getConnection() throws java.sql.SQLException {
            return probeSource.getConnection();
        }

        @Override
        public void shutdown() {
            // 连接归 Spring 管理的池，不在此关闭
        }

        @Override
        public void initialize() {
            // 数据源已就绪，无初始化诉求
        }
    }
}
