package com.eladminplus.system.task;

import java.lang.reflect.Method;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Quartz 任务执行壳：按 JobDataMap 里的 bean_name / method_name 取 Spring Bean，
 * 反射调用 @TaskMethod 白名单方法（参数单个 String 原样传入）。
 */
public class QuartzTaskJob implements Job, ApplicationContextAware {

    public static final String KEY_TASK_ID = "taskId";
    public static final String KEY_BEAN_NAME = "beanName";
    public static final String KEY_METHOD_NAME = "methodName";
    public static final String KEY_PARAM = "param";

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
        Object bean = applicationContext.getBean(beanName);
        Method method = resolveMethod(bean, methodName);
        try {
            if (method.getParameterCount() == 0) {
                method.invoke(bean);
            } else {
                method.invoke(bean, param);
            }
        } catch (Exception e) {
            throw new IllegalStateException("任务执行失败：" + beanName + "#" + methodName, e);
        }
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
}
