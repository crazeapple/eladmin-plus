package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 字典保存请求：id 为空即新增；name 全局唯一。 */
public record DictSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String description) {}
