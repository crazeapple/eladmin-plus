package com.eladminplus.system.task;

import com.eladminplus.system.domain.TaskLog;
import com.eladminplus.system.service.TaskLogService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Quartz 任务执行壳：按 JobDataMap 里的 bean_name / method_name 取 Spring Bean，
 * 反射调用 @TaskMethod 白名单方法（参数单个 String 原样传入）；
 * 成功与失败都异步落 task_log（执行时间、结果、耗时、异常摘要）。
 */
public class QuartzTaskJob implements Job, ApplicationContextAware {

    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_BEAN_NAME = "beanName";
    public static final String KEY_METHOD_NAME = "methodName";
    public static final String KEY_PARAM = "param";

    /** 异常摘要截断上限（error_msg 为 TEXT，摘要语义不收整堆栈） */
    private static final int ERROR_SUMMARY_MAX = 2000;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void execute(JobExecutionContext context) {
        var data = context.getMergedJobDataMap();
        String beanName = data.getString(KEY_BEAN_NAME);
        String methodName = data.getString(KEY_METHOD_NAME);
        String param = data.getString(KEY_PARAM);
        Long taskId = data.getLong(KEY_TASK_ID);

        LocalDateTime executeTime = LocalDateTime.now();
        long start = System.currentTimeMillis();
        int success = 1;
        String errorMsg = null;
        try {
            Object bean = applicationContext.getBean(beanName);
            Method method = resolveMethod(bean, methodName);
            if (method.getParameterCount() == 0) {
                method.invoke(bean);
            } else {
                method.invoke(bean, param);
            }
        } catch (Exception e) {
            success = 0;
            errorMsg = summarize(e);
        }
        long durationMs = System.currentTimeMillis() - start;

        TaskLog entry = new TaskLog();
        entry.setTaskId(taskId);
        entry.setExecuteTime(executeTime);
        entry.setSuccess(success);
        entry.setDurationMs(durationMs);
        entry.setErrorMsg(errorMsg);
        applicationContext.getBean(TaskLogService.class).record(entry);
    }

    private Method resolveMethod(Object bean, String methodName) {
        for (Method method : bean.getClass().getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params.length == 0 || (params.length == 1 && params[0] == String.class)) {
                return method;
            }
        }
        throw new IllegalStateException("白名单方法不存在：" + methodName);
    }

    /** 异常摘要：类名 + 根因消息，超长截断 */
    private String summarize(Exception e) {
        Throwable cause = e instanceof InvocationTargetException invocationTarget
                ? invocationTarget.getTargetException()
                : e;
        Throwable root = cause;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String summary = root.getClass().getSimpleName() + ": " + root.getMessage();
        return summary.length() > ERROR_SUMMARY_MAX ? summary.substring(0, ERROR_SUMMARY_MAX) : summary;
    }
}
