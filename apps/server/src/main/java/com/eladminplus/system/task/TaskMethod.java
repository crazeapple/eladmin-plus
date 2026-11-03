package com.eladminplus.system.task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务目标白名单标记：方法级注解，扫出的清单即任务表单下拉数据源，
 * 未标记方法不可被任务引用（消灭任意反射调用面）。方法签名约定：单个 String 参数（可空）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TaskMethod {

    /** 参数说明（表单提示） */
    String value() default "";
}
