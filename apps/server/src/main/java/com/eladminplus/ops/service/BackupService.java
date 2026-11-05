package com.eladminplus.ops.service;

import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.domain.BackupRecord;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.mapper.BackupRecordMapper;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 手动备份服务：目录不存在自动创建（mkdir 语义）、不可创建或不可写 5xx 明确报因 → 容器内 mysqldump
 * stdout 流直写目标文件 → 落 backup_record（大小取落盘实际字节数）。文件名 {库名}_{yyyyMMddHHmmss}.sql，
 * 同秒重复覆盖（概率极低不作防护）。执行失败 5xx 不落记录（记录表即成功备份账本），半截文件就地清理。
 */
@Service
public class BackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DatabaseConfigMapper databaseConfigMapper;
    private final BackupRecordMapper backupRecordMapper;
    private final MysqlDumpExecutor mysqlDumpExecutor;

    public BackupService(
            DatabaseConfigMapper databaseConfigMapper,
            BackupRecordMapper backupRecordMapper,
            MysqlDumpExecutor mysqlDumpExecutor) {
        this.databaseConfigMapper = databaseConfigMapper;
        this.backupRecordMapper = backupRecordMapper;
        this.mysqlDumpExecutor = mysqlDumpExecutor;
    }

    /** 备份：库名与连接凭据取自连接配置；连接配置无则 404 */
    public BackupRecord backup(Long configId, String dir) {
        DatabaseConfig config = databaseConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        if (!StringUtils.hasText(dir)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
        Path target = prepare(config.getName(), dir);
        try {
            mysqlDumpExecutor.dump(config.getName(), config.getUserName(), config.getPassword(), target);
        } catch (BusinessException e) {
            deleteQuietly(target);
            throw e;
        }
        long size;
        try {
            size = Files.size(target);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份文件大小读取失败（" + e.getMessage() + "）");
        }

        BackupRecord record = new BackupRecord();
        record.setFileName(target.getFileName().toString());
        record.setDir(target.getParent().toString());
        record.setDbName(config.getName());
        record.setSize(size);
        backupRecordMapper.insert(record);
        return record;
    }

    /** 目标文件定位与目录保障：目录归一为绝对路径（记录自描述，删记录不依赖进程工作目录） */
    private static Path prepare(String database, String dir) {
        Path target;
        try {
            Path base = Path.of(dir).toAbsolutePath().normalize();
            target = base.resolve(database + "_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".sql");
            Files.createDirectories(base);
        } catch (InvalidPathException | IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份目录不可创建（" + dir + "）");
        }
        if (!Files.isWritable(target.getParent())) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份目录不可写（" + dir + "）");
        }
        return target;
    }

    private static void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 半截文件清理失败不阻断错误响应：记录未落库，文件无账目
        }
    }
}
