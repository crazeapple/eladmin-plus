package com.eladminplus.system.dto;

/** 字典消费视图：useDict 数据源契约——label 多语言 JSON 原样下发，前端按当前 locale 解析。 */
public record DictItemView(String label, String value, Integer sort) {}
