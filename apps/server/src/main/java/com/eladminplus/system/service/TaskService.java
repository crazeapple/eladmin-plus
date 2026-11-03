package com.eladminplus.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.system.domain.Task;
import com.eladminplus.system.domain.TaskLog;
import com.eladminplus.system.dto.TaskSaveRequest;
import com.eladminplus.system.dto.TaskTargetView;
import com.eladminplus.system.dto.TaskView;
import com.eladminplus.system.mapper.TaskLogMapper;
import com.eladminplus.system.mapper.TaskMapper;
import com.eladminplus.system.task.QuartzTaskJob;
import com.eladminplus.system.task.TaskMethodScanner;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 定时任务管理：CRUD 全链；目标须在 @TaskMethod 白名单内（422）、cron 合法性后端校验（422）、
 * 任务名唯一（409）；创建即按 cron 注册调度，更新重排，删除即 unschedule（暂停态同删）。
 */
@Service
public class TaskService {

    /** Quartz job / trigger 组名与命名前缀（按任务 id 命名，改名不断链） */
    public static final String JOB_GROUP = "system-task";
    public static final String JOB_NAME_PREFIX = "task-";

    private final TaskMapper taskMapper;
    private final TaskLogMapper taskLogMapper;
    private final TaskMethodScanner taskMethodScanner;
    private final Scheduler scheduler;

    public TaskService(
            TaskMapper taskMapper,
            TaskLogMapper taskLogMapper,
            TaskMethodScanner taskMethodScanner,
            Scheduler scheduler) {
        this.taskMapper = taskMapper;
        this.taskLogMapper = taskLogMapper;
        this.taskMethodScanner = taskMethodScanner;
        this.scheduler = scheduler;
    }

    /** 任务列表：调度态以 scheduler 实际状态为准（running / paused / none） */
    public List<TaskView> list() {
        return taskMapper.selectList(Wrappers.<Task>lambdaQuery().orderByAsc(Task::getId)).stream()
                .map(task -> new TaskView(
                        task.getId(),
                        task.getName(),
                        task.getBeanName(),
                        task.getMethodName(),
                        task.getParam(),
                        task.getCron(),
                        task.getStatus(),
                        scheduleStateOf(task.getId())))
                .toList();
    }

    @Transactional
    public Long save(TaskSaveRequest request) {
        if (!CronExpression.isValidExpression(request.cron())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.system.task-cron-invalid");
        }
        checkTargetWhitelisted(request.beanName(), request.methodName());
        Task task = new Task();
        task.setId(request.id());
        task.setName(request.name());
        task.setBeanName(request.beanName());
        task.setMethodName(request.methodName());
        task.setParam(request.param());
        task.setCron(request.cron());
        task.setStatus(request.status() == null ? 1 : request.status());
        Long dup = taskMapper.selectCount(Wrappers.<Task>lambdaQuery()
                .eq(Task::getName, task.getName())
                .ne(task.getId() != null, Task::getId, task.getId()));
        if (dup > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "error.system.task-name-duplicate");
        }
        if (task.getId() == null) {
            taskMapper.insert(task);
        } else {
            if (taskMapper.selectById(task.getId()) == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
            }
            // 全量表单提交语义：显式逐列覆盖（param 可清空）
            taskMapper.update(
                    Wrappers.<Task>lambdaUpdate()
                            .eq(Task::getId, task.getId())
                            .set(Task::getName, task.getName())
                            .set(Task::getBeanName, task.getBeanName())
                            .set(Task::getMethodName, task.getMethodName())
                            .set(Task::getParam, task.getParam())
                            .set(Task::getCron, task.getCron())
                            .set(Task::getStatus, task.getStatus()));
        }
        // 创建即注册，更新重排（replace 原子替换既有 job 与 trigger），再按期望态校准暂停
        schedule(task);
        return task.getId();
    }

    @Transactional
    public void remove(Long id) {
        if (taskMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        try {
            scheduler.deleteJob(jobKeyOf(id)); // 暂停态同样删除
        } catch (SchedulerException e) {
            throw new IllegalStateException("任务调度移除失败：" + id, e);
        }
        taskMapper.deleteById(id);
    }

    /** 暂停：trigger 状态翻 PAUSED，期望态同步置 0（重启后持久化恢复含暂停态） */
    @Transactional
    public void pause(Long id) {
        requireTask(id);
        try {
            scheduler.pauseTrigger(triggerKeyOf(id));
        } catch (SchedulerException e) {
            throw new IllegalStateException("任务暂停失败：" + id, e);
        }
        taskMapper.update(Wrappers.<Task>lambdaUpdate().eq(Task::getId, id).set(Task::getStatus, 0));
    }

    /** 恢复：重新排程，期望态同步置 1 */
    @Transactional
    public void resume(Long id) {
        requireTask(id);
        try {
            scheduler.resumeTrigger(triggerKeyOf(id));
        } catch (SchedulerException e) {
            throw new IllegalStateException("任务恢复失败：" + id, e);
        }
        taskMapper.update(Wrappers.<Task>lambdaUpdate().eq(Task::getId, id).set(Task::getStatus, 1));
    }

    /** 立即执行一次：异步触发不阻塞请求，当次执行受暂停态影响与否由 Quartz 语义决定 */
    public void executeOnce(Long id) {
        requireTask(id);
        try {
            scheduler.triggerJob(jobKeyOf(id));
        } catch (SchedulerException e) {
            throw new IllegalStateException("任务触发失败：" + id, e);
        }
    }

    /** 执行日志分页（按任务过滤；新执行在前） */
    public PageResult<TaskLog> logs(Long taskId, int page, int size) {
        requireTask(taskId);
        Page<TaskLog> result = taskLogMapper.selectPage(
                new PageRequest(page, size).toMpPage(),
                Wrappers.<TaskLog>lambdaQuery()
                        .eq(TaskLog::getTaskId, taskId)
                        .orderByDesc(TaskLog::getId));
        return PageResult.from(result);
    }

    private void requireTask(Long id) {
        if (taskMapper.selectById(id) == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
    }

    /** 注册 / 重排调度（replace 覆盖既有定义），期望态 0 即注册后暂停 */
    private void schedule(Task task) {
        JobKey jobKey = jobKeyOf(task.getId());
        JobDetail jobDetail = JobBuilder.newJob(QuartzTaskJob.class)
                .withIdentity(jobKey)
                .usingJobData(QuartzTaskJob.KEY_TASK_ID, task.getId())
                .usingJobData(QuartzTaskJob.KEY_BEAN_NAME, task.getBeanName())
                .usingJobData(QuartzTaskJob.KEY_METHOD_NAME, task.getMethodName())
                .usingJobData(QuartzTaskJob.KEY_PARAM, task.getParam() == null ? "" : task.getParam())
                .build();
        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(triggerKeyOf(task.getId()))
                .forJob(jobDetail)
                .withSchedule(CronScheduleBuilder.cronSchedule(task.getCron()))
                .build();
        try {
            scheduler.scheduleJob(jobDetail, Set.of(trigger), true);
            if (task.getStatus() != null && task.getStatus() == 0) {
                scheduler.pauseTrigger(triggerKeyOf(task.getId()));
            } else {
                scheduler.resumeTrigger(triggerKeyOf(task.getId()));
            }
        } catch (SchedulerException e) {
            throw new IllegalStateException("任务调度注册失败：" + task.getId(), e);
        }
    }

    /** scheduler 实际调度态：PAUSED → paused；无 trigger → none；其余 → running */
    private String scheduleStateOf(Long taskId) {
        try {
            return switch (scheduler.getTriggerState(triggerKeyOf(taskId))) {
                case PAUSED -> "paused";
                case NONE -> "none";
                default -> "running";
            };
        } catch (SchedulerException e) {
            return "none";
        }
    }

    private void checkTargetWhitelisted(String beanName, String methodName) {
        Set<String> whitelist = whitelistKeys();
        boolean beanPresent = whitelist.stream().anyMatch(key -> key.startsWith(beanName + "#"));
        if (!beanPresent) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.system.task-bean-not-whitelisted");
        }
        if (!whitelist.contains(beanName + "#" + methodName)) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "error.system.task-method-not-whitelisted");
        }
    }

    private Set<String> whitelistKeys() {
        Set<String> keys = new HashSet<>();
        for (TaskTargetView target : taskMethodScanner.scan()) {
            keys.add(target.beanName() + "#" + target.methodName());
        }
        return keys;
    }

    public static JobKey jobKeyOf(Long taskId) {
        return JobKey.jobKey(JOB_NAME_PREFIX + taskId, JOB_GROUP);
    }

    public static TriggerKey triggerKeyOf(Long taskId) {
        return TriggerKey.triggerKey(JOB_NAME_PREFIX + taskId, JOB_GROUP);
    }
}
