package com.eladminplus.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 用户保存请求：id 为空即新增（password 为初始密码，必填）；编辑不改密码（改密走重置与个人中心）。
 * roleIds / jobIds 全量替换语义。
 */
public record UserSaveRequest(
        Long id,
        @NotBlank @Size(max = 64) String username,
        @Size(max = 64) String nickName,
        @NotBlank @Size(max = 128) String email,
        @Size(max = 32) String phone,
        Integer gender,
        @NotNull Integer enabled,
        Long deptId,
        String password,
        List<Long> roleIds,
        List<Long> jobIds) {}
