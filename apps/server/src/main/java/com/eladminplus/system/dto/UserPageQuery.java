package com.eladminplus.system.dto;

/** 用户分页查询：page 0 起；部门过滤含其子级；名称与邮箱为默认语言列模糊；时间为创建时间范围（闭区间，ISO 形态）。 */
public record UserPageQuery(
        Integer page,
        Integer size,
        Long deptId,
        String name,
        String email,
        Integer enabled,
        String startTime,
        String endTime) {}
