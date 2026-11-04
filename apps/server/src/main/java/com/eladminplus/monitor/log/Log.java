package com.eladminplus.monitor.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志标记：方法级注解，标注端点被调用后异步落 operation_log
 * （操作人、IP、描述、耗时、请求参数五字段齐）；未标注端点不落行。
 * 敏感接口（登录、改密）不标注即不误记。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 操作描述（日志列表展示文案） */
    String value() default "";
}
