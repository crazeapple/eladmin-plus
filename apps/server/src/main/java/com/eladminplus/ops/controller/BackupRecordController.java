package com.eladminplus.ops.controller;

import com.eladminplus.common.R;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.ops.dto.BackupRecordPageQuery;
import com.eladminplus.ops.dto.BackupRecordView;
import com.eladminplus.ops.service.BackupRecordService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 备份记录端点：分页列表 / 删除（同删备份文件）。权限点随 database:list / del（ROLE_ROOT 直通）。 */
@RestController
@RequestMapping("/database/records")
public class BackupRecordController {

    private final BackupRecordService backupRecordService;

    public BackupRecordController(BackupRecordService backupRecordService) {
        this.backupRecordService = backupRecordService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('database:list') || hasAuthority('ROLE_ROOT')")
    public R<PageResult<BackupRecordView>> page(BackupRecordPageQuery query) {
        return R.ok(backupRecordService.page(query));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('database:del') || hasAuthority('ROLE_ROOT')")
    public R<Void> remove(@PathVariable Long id) {
        backupRecordService.remove(id);
        return R.ok();
    }
}
