package com.eladminplus.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 角色视图：name 已按当前请求语言解析；customDeptIds 仅 CUSTOM 档有值。 */
public record RoleView(
        Long id,
        String code,
        String name,
        String description,
        Integer level,
        String dataScope,
        List<Long> customDeptIds,
        Integer enabled,
        Boolean builtIn,
        LocalDateTime createdAt) {}
