package com.eladminplus.storage.dto;

import com.eladminplus.storage.domain.StorageFile;
import java.time.LocalDateTime;

/** 存储文件视图：存储键不外发（内部形态），前端下载一律走 /storage/files/{id}/download。 */
public record StorageFileView(
        Long id, String name, String ext, Long size, String mime, String engine, String uploader, LocalDateTime createdAt) {

    public static StorageFileView from(StorageFile row) {
        return new StorageFileView(
                row.getId(),
                row.getName(),
                row.getExt(),
                row.getSize(),
                row.getMime(),
                row.getEngine(),
                row.getUploader(),
                row.getCreatedAt());
    }
}
