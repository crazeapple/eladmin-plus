package com.eladminplus.common.data;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 分页请求：page 从 0 起（与 Spring Data 语义对齐）；sort 为可选排序描述，随首个真实查询需求定形。
 */
public record PageRequest(int page, int size, String sort) {

    public PageRequest(int page, int size) {
        this(page, size, null);
    }

    /** 转 MyBatis-Plus 分页：current 从 1 起，页码换算集中在此 */
    public <T> Page<T> toMpPage() {
        return Page.of(page + 1L, size);
    }
}
