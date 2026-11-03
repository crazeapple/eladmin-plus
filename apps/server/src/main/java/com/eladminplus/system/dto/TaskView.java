package com.eladminplus.system.dto;

/**
 * 任务列表视图：scheduleState 为 scheduler 实际调度态（running / paused / none），
 * 与表字段 status（期望态）分离——运行态以 Quartz 为权威。
 */
public record TaskView(
        Long id,
        String name,
        String beanName,
        String methodName,
        String param,
        String cron,
        Integer status,
        String scheduleState) {}
