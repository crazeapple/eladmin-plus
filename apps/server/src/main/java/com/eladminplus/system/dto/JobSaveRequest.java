package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 岗位保存请求：id 为空即新增；nameEn 全量同步语义同部门（null 即清除覆盖）。 */
public record JobSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String nameEn,
        Integer sort,
        @NotNull Integer enabled) {}
