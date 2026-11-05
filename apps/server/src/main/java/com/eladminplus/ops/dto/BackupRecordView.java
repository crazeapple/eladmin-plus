package com.eladminplus.ops.dto;

import com.eladminplus.storage.domain.StorageFile;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/** 备份记录视图（storage_file source=backup 的视图形态）：库名从原名前缀解析展示，仅展示用途。 */
public record BackupRecordView(Long id, String fileName, String dbName, Long size, LocalDateTime createdAt) {

    /** 原名形态 {库名}_{yyyyMMddHHmmss}.sql（库名可含下划线，锚定尾部时间戳拆分） */
    private static final Pattern DB_NAME_PATTERN = Pattern.compile("^(.+)_(\\d{14})\\.sql$");

    public static BackupRecordView from(StorageFile row) {
        return new BackupRecordView(row.getId(), row.getName(), dbNameOf(row.getName()), row.getSize(), row.getCreatedAt());
    }

    /** 库名解析：形态不符（异常数据）不猜，回空展示 */
    private static String dbNameOf(String fileName) {
        var matcher = DB_NAME_PATTERN.matcher(fileName);
        return matcher.matches() ? matcher.group(1) : null;
    }
}
