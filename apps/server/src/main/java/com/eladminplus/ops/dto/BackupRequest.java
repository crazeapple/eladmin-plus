package com.eladminplus.ops.dto;

/** 手动备份请求：dir 为落盘目录（不存在自动创建）。 */
public record BackupRequest(String dir) {}
