package com.eladminplus.system.dto;

/** 任务目标视图：白名单清单条目（Bean 名、方法名、参数说明），任务表单下拉数据源。 */
public record TaskTargetView(String beanName, String methodName, String param) {}
