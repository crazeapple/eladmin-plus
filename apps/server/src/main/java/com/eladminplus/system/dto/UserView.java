package com.eladminplus.system.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 用户列表视图：deptName 与 jobNames 已按当前请求语言解析；roleIds 供编辑表单回显。 */
public record UserView(
        Long id,
        String username,
        String nickName,
        String email,
        String phone,
        Integer gender,
        Integer enabled,
        LocalDateTime createdAt,
        Long deptId,
        String deptName,
        List<Long> roleIds,
        List<Long> jobIds,
        List<String> jobNames) {}
