package com.eladminplus.ops.dto;

/** 备份记录分页查询：page 0 起；filename 为文件名模糊筛选（记录量随备份频次增长，列表按时间倒序浏览）。 */
public record BackupRecordPageQuery(Integer page, Integer size, String filename) {}
