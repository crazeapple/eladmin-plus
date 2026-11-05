package com.eladminplus.ops.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.ops.dto.BackupRecordPageQuery;
import com.eladminplus.ops.dto.BackupRecordView;
import com.eladminplus.ops.service.BackupRecordService;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.service.StorageService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 备份记录端点（storage_file source=backup 视图）：分页 / 删除代理 / 导出代理。权限点 database:list / del / backup（ROLE_ROOT 直通）。 */
@RestController
@RequestMapping("/database/records")
public class BackupRecordController {

    private final BackupRecordService backupRecordService;
    private final StorageService storageService;

    public BackupRecordController(BackupRecordService backupRecordService, StorageService storageService) {
        this.backupRecordService = backupRecordService;
        this.storageService = storageService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('database:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<BackupRecordView>> page(BackupRecordPageQuery query) {
        return R.ok(backupRecordService.page(query));
    }

    /** 导出代理：内部走存储域按行内引擎字段路由下载，流式回原名（RFC 5987 编码）；已删文件 404 提示重新备份 */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasAuthority('database:backup') || hasAuthority('ROLE_ROOT')")
    public ResponseEntity<InputStreamResource> export(@PathVariable Long id) {
        StorageFile row = backupRecordService.exportable(id);
        InputStream in = storageService.open(row);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(row.getName()))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(in));
    }

    /** 删除代理：内部复用存储域先删对象后删行语义，存储管理页删除备份文件同样生效 */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('database:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        backupRecordService.remove(id);
        return R.ok();
    }

    /** 导出响应头：原名经 RFC 5987 编码（非 ASCII 名不乱码），形态 attachment; filename=… */
    private static String contentDisposition(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build()
                .toString();
    }
}
