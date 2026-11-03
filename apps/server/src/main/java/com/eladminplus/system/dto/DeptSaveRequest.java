package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 部门保存请求：id 为空即新增；nameEn 为 null 视为无英文覆盖（全量同步语义，保存即清除旧覆盖）。 */
public record DeptSaveRequest(
        Long id,
        Long parentId,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String nameEn,
        Integer sort,
        @NotNull Integer enabled) {}
