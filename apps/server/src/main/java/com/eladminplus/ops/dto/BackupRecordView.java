package com.eladminplus.ops.dto;

import com.eladminplus.ops.domain.BackupRecord;
import java.time.LocalDateTime;

/** 备份记录视图：目录为绝对路径，下载走宿主机目录（无下载端点）。 */
public record BackupRecordView(Long id, String fileName, String dir, String dbName, Long size, LocalDateTime createdAt) {

    public static BackupRecordView from(BackupRecord row) {
        return new BackupRecordView(
                row.getId(), row.getFileName(), row.getDir(), row.getDbName(), row.getSize(), row.getCreatedAt());
    }
}
