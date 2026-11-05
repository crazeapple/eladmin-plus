package com.eladminplus.ops.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.dto.BackupRecordPageQuery;
import com.eladminplus.ops.dto.BackupRecordView;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.mapper.StorageFileMapper;
import com.eladminplus.storage.service.StorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 备份记录服务（storage_file source=backup 的视图形态）：有文件即有记录、文件删则记录无，与存储
 * 管理页同一真相源。删除与导出为 ops 代理，内部复用存储域「先删对象后删行」与按行内引擎路由下载；
 * 非备份来源行（页面手传文件）不属记录视图，按 404 语义拒绝。
 */
@Service
public class BackupRecordService {

    private final StorageFileMapper storageFileMapper;
    private final StorageService storageService;

    public BackupRecordService(StorageFileMapper storageFileMapper, StorageService storageService) {
        this.storageFileMapper = storageFileMapper;
        this.storageService = storageService;
    }

    /** 备份记录分页：文件名模糊、新记录在前 */
    public PageResult<BackupRecordView> page(BackupRecordPageQuery query) {
        var wrapper = Wrappers.<StorageFile>lambdaQuery().eq(StorageFile::getSource, StorageService.SOURCE_BACKUP);
        if (StringUtils.hasText(query.filename())) {
            wrapper.like(StorageFile::getName, query.filename());
        }
        wrapper.orderByDesc(StorageFile::getId);
        Page<StorageFile> result =
                storageFileMapper.selectPage(new PageRequest(pageOf(query), sizeOf(query)).toMpPage(), wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(BackupRecordView::from).toList(),
                result.getTotal(),
                (int) (result.getCurrent() - 1),
                (int) result.getSize());
    }

    /** 删除代理：复用存储域先删对象后删行语义；文件已失仅删记录（对象删除幂等） */
    public void remove(Long id) {
        storageService.remove(backupRow(id).getId());
    }

    /** 导出代理行读取：按行内引擎字段路由下载由存储域承接 */
    public StorageFile exportable(Long id) {
        return backupRow(id);
    }

    /** 行读取：行不在或非备份来源（手传文件不混入记录视图）一律 404 */
    private StorageFile backupRow(Long id) {
        StorageFile row = storageFileMapper.selectById(id);
        if (row == null || !StorageService.SOURCE_BACKUP.equals(row.getSource())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        return row;
    }

    private static int pageOf(BackupRecordPageQuery query) {
        return query.page() == null ? 0 : query.page();
    }

    private static int sizeOf(BackupRecordPageQuery query) {
        return query.size() == null ? 10 : query.size();
    }
}
