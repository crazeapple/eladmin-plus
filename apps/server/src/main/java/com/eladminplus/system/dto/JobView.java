package com.eladminplus.system.dto;

/** 岗位列表视图：name 已按当前请求语言解析。 */
public record JobView(Long id, String name, Integer sort, Integer enabled) {}
