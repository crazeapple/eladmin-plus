package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 角色保存请求：id 为空即新增；编辑时 code 忽略（角色标识创建后不可改，权限点引用稳定性）。
 * nameEn 全量同步语义同部门；customDeptIds 仅 dataScope=CUSTOM 时生效。
 */
public record RoleSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Z0-9_]+$", message = "角色码为大写下划线形态") String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 255) String nameEn,
        @Size(max = 255) String description,
        Integer level,
        @NotNull @Pattern(regexp = "^(ALL|DEPT|CUSTOM)$", message = "数据范围三档") String dataScope,
        List<Long> customDeptIds,
        Integer enabled) {}
