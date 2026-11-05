package com.eladminplus.ops.dto;

/** 备份记录分页查询：page 0 起；filename 为文件名模糊筛选（数据源 storage_file source=backup，时间倒序浏览）。 */
public record BackupRecordPageQuery(Integer page, Integer size, String filename) {}
