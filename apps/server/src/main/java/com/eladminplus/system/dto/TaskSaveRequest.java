package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 任务保存请求：id 为空即新增；目标（Bean 名 + 方法名）须在 @TaskMethod 白名单内，
 * cron 后端校验合法性；status 为期望态（1 启用 0 暂停）。
 */
public record TaskSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 128) String beanName,
        @NotBlank @Size(max = 128) String methodName,
        @Size(max = 255) String param,
        @NotBlank @Size(max = 128) String cron,
        Integer status) {}
