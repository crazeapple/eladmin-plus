package com.eladminplus.ops.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.domain.BackupRecord;
import com.eladminplus.ops.dto.BackupRecordPageQuery;
import com.eladminplus.ops.dto.BackupRecordView;
import com.eladminplus.ops.mapper.BackupRecordMapper;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 备份记录服务：分页列表（新记录在前）；删除先删文件后删行——文件删除失败行保留（记录与文件一一对应，
 * 防孤儿记录），文件已失仅删记录保操作可完成。
 */
@Service
public class BackupRecordService {

    private final BackupRecordMapper backupRecordMapper;

    public BackupRecordService(BackupRecordMapper backupRecordMapper) {
        this.backupRecordMapper = backupRecordMapper;
    }

    /** 备份记录分页：文件名模糊、新记录在前 */
    public PageResult<BackupRecordView> page(BackupRecordPageQuery query) {
        var wrapper = Wrappers.<BackupRecord>lambdaQuery();
        if (StringUtils.hasText(query.filename())) {
            wrapper.like(BackupRecord::getFileName, query.filename());
        }
        wrapper.orderByDesc(BackupRecord::getId);
        Page<BackupRecord> result =
                backupRecordMapper.selectPage(new PageRequest(pageOf(query), sizeOf(query)).toMpPage(), wrapper);
        return new PageResult<>(
                result.getRecords().stream().map(BackupRecordView::from).toList(),
                result.getTotal(),
                (int) (result.getCurrent() - 1),
                (int) result.getSize());
    }

    /** 删除：先删备份文件后删记录行；文件已失（deleteIfExists 返回否）仅删记录 */
    public void remove(Long id) {
        BackupRecord row = get(id);
        Path file;
        try {
            file = Path.of(row.getDir()).resolve(row.getFileName());
        } catch (InvalidPathException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份文件删除失败：路径不合法（" + row.getDir() + "）");
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份文件删除失败：" + e.getMessage());
        }
        backupRecordMapper.deleteById(id);
    }

    /** 行读取：不存在 id 按语义拒 404 */
    private BackupRecord get(Long id) {
        BackupRecord row = backupRecordMapper.selectById(id);
        if (row == null) {
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
