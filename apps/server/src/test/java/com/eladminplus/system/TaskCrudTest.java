package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.auth.session.TokenService.TokenPair;
import com.eladminplus.system.domain.Task;
import com.eladminplus.system.dto.TaskSaveRequest;
import com.eladminplus.system.mapper.TaskMapper;
import com.eladminplus.system.service.TaskService;
import com.eladminplus.system.task.TaskMethod;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.TriggerKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** 定时任务 CRUD：创建即注册调度、唯一与校验语义（409 / 422）、更新重排、删除即 unschedule。 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskCrudTest {

    /** 测试任务名前缀，清理定位用 */
    private static final String TEST_NAME_PREFIX = "testtask.";

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
    private Scheduler scheduler;

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
            taskMapper.deleteById(id);
        }
    }

    /** 测试桩任务 Bean：与白名单测试独立注册，供目标校验用例引用 */
    @TestConfiguration
    static class StubTasks {

        @Bean
        StubTask stubTask() {
            return new StubTask();
        }
    }

    static class StubTask {

        @TaskMethod("样例参数")
        public void sample(String param) {
            // 白名单桩：测试中不落调度触发（cron 均为远期时点）
        }

        @TaskMethod("无参样例")
        public void noArg() {
            // 白名单桩
        }
    }

    private Long create(TaskSaveRequest request) throws Exception {
        MvcResult result = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").asLong();
    }

    private TaskSaveRequest validRequest(String name, Long id) {
        return new TaskSaveRequest(id, name, "stubTask", "sample", "param-text", "0 0 6 * * ?", 1);
    }

    @Test
    void 创建白名单目标任务即注册调度() throws Exception {
        Long id = create(validRequest(TEST_NAME_PREFIX + "register", null));

        JobKey jobKey = TaskService.jobKeyOf(id);
        assertThat(scheduler.checkExists(jobKey)).isTrue();
        CronTrigger trigger =
                (CronTrigger) scheduler.getTrigger(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup()));
        assertThat(trigger).isNotNull();
        assertThat(trigger.getCronExpression()).isEqualTo("0 0 6 * * ?");
        assertThat(scheduler.getTriggerState(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup())))
                .isEqualTo(org.quartz.Trigger.TriggerState.NORMAL);

        // 列表调度态以 scheduler 为准：启用任务 running
        MvcResult listResult = mockMvc
                .perform(get("/tasks").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andReturn();
        String body = listResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"scheduleState\":\"running\"");
    }

    @Test
    void 重名409与非法cron422() throws Exception {
        create(validRequest(TEST_NAME_PREFIX + "dup", null));
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                validRequest(TEST_NAME_PREFIX + "dup", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("任务名已存在"));

        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskSaveRequest(
                                null, TEST_NAME_PREFIX + "badcron", "stubTask", "sample", null,
                                "not-a-cron", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("cron 表达式不合法"));
    }

    @Test
    void 白名单外目标拒绝422() throws Exception {
        // 白名单外 Bean
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskSaveRequest(
                                null, TEST_NAME_PREFIX + "badbean", "no-such-bean", "any", null,
                                "0 0 6 * * ?", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("目标 Bean 不在任务白名单"));

        // 白名单内 Bean 但方法不在白名单
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskSaveRequest(
                                null, TEST_NAME_PREFIX + "badmethod", "stubTask", "notWhitelisted",
                                null, "0 0 6 * * ?", 1))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("目标方法不在任务白名单"));
    }

    @Test
    void 更新目标或cron后重排生效() throws Exception {
        Long id = create(validRequest(TEST_NAME_PREFIX + "reschedule", null));

        mockMvc.perform(put("/tasks/{id}", id)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskSaveRequest(
                                null, TEST_NAME_PREFIX + "reschedule", "stubTask", "noArg", null,
                                "0 30 7 * * ?", 1))))
                .andExpect(status().isOk());

        JobKey jobKey = TaskService.jobKeyOf(id);
        CronTrigger trigger =
                (CronTrigger) scheduler.getTrigger(TriggerKey.triggerKey(jobKey.getName(), jobKey.getGroup()));
        assertThat(trigger.getCronExpression()).isEqualTo("0 30 7 * * ?");
        JobDetail jobDetail = scheduler.getJobDetail(jobKey);
        assertThat(jobDetail.getJobDataMap().getString("beanName")).isEqualTo("stubTask");
        assertThat(jobDetail.getJobDataMap().getString("methodName")).isEqualTo("noArg");
    }

    @Test
    void 删除即unschedule() throws Exception {
        Long id = create(validRequest(TEST_NAME_PREFIX + "remove", null));
        assertThat(scheduler.checkExists(TaskService.jobKeyOf(id))).isTrue();

        mockMvc.perform(delete("/tasks/{id}", id).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        assertThat(scheduler.checkExists(TaskService.jobKeyOf(id))).isFalse();
        assertThat(taskMapper.selectById(id)).isNull();
    }
}
