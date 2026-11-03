package com.eladminplus.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 菜单保存请求：id 为空即新增；type 创建后不可变更（权限点生命周期绑死菜单树）。
 * i18n key 与组件路径 key 的前端侧存在性由视图映射与语言包把关，后端只管结构。
 */
public record MenuSaveRequest(
        Long id,
        Long parentId,
        @NotNull @Min(0) @Max(2) Integer type,
        @Size(max = 128) String permission,
        @Size(max = 128) String i18nKey,
        @Size(max = 128) String componentPathKey,
        @Size(max = 128) String icon,
        Boolean hidden,
        Boolean noCache,
        Integer sort) {}
