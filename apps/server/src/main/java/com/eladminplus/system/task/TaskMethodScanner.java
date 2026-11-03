package com.eladminplus.system.task;

import com.eladminplus.system.dto.TaskTargetView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Component;

/**
 * @TaskMethod 白名单扫描：遍历容器 Bean 定义反射出标记方法清单。
 * 仅收编符合签名约定的方法（无参或单个 String 参数），其余标记视为无效不进清单。
 */
@Component
public class TaskMethodScanner {

    private final ConfigurableListableBeanFactory beanFactory;

    public TaskMethodScanner(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    /** 白名单清单（Bean 名、方法名、参数说明），Bean 名 + 方法名排序稳定输出 */
    public List<TaskTargetView> scan() {
        List<TaskTargetView> targets = new ArrayList<>();
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanType == null) {
                continue;
            }
            for (Method method : beanType.getMethods()) {
                TaskMethod marker = method.getAnnotation(TaskMethod.class);
                if (marker == null || !conformingSignature(method)) {
                    continue;
                }
                targets.add(new TaskTargetView(beanName, method.getName(), marker.value()));
            }
        }
        targets.sort(Comparator.comparing(TaskTargetView::beanName).thenComparing(TaskTargetView::methodName));
        return targets;
    }

    /** 签名约定：无参或单个 String 参数 */
    private boolean conformingSignature(Method method) {
        Class<?>[] params = method.getParameterTypes();
        return params.length == 0 || (params.length == 1 && params[0] == String.class);
    }
}
