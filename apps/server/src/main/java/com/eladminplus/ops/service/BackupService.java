package com.eladminplus.ops.service;

import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.domain.DatabaseConfig;
import com.eladminplus.ops.mapper.DatabaseConfigMapper;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.storage.service.StorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 手动备份服务：sidecar 转储端点的 gzip 流直写系统临时文件（先验 dump 响应码，失败不产生任何
 * 存储对象与行），过验后经存储域上传落当前配置引擎并打标 source=backup，storage_file 行在册
 * （原名 {库名}_{yyyyMMddHHmmss}.sql.gz，大小取实际字节数）。存储键 uuid 唯一，无同名覆盖问题。
 */
@Service
public class BackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DatabaseConfigMapper databaseConfigMapper;
    private final StorageService storageService;
    private final MysqlDumpExecutor mysqlDumpExecutor;

    public BackupService(
            DatabaseConfigMapper databaseConfigMapper, StorageService storageService, MysqlDumpExecutor mysqlDumpExecutor) {
        this.databaseConfigMapper = databaseConfigMapper;
        this.storageService = storageService;
        this.mysqlDumpExecutor = mysqlDumpExecutor;
    }

    /** 备份：库名与连接凭据取自连接配置；连接配置无则 404；执行失败 5xx 报「备份执行失败」原因，无行 */
    public StorageFile backup(Long configId, String operator) {
        DatabaseConfig config = databaseConfigMapper.selectById(configId);
        if (config == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        String fileName = config.getName() + "_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".sql.gz";
        Path staging = createStaging();
        try {
            mysqlDumpExecutor.dump(config.getName(), config.getUserName(), config.getPassword(), staging);
            long size;
            try {
                size = Files.size(staging);
            } catch (IOException e) {
                throw new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份文件大小读取失败（" + e.getMessage() + "）");
            }
            try (InputStream in = Files.newInputStream(staging)) {
                return storageService.storeBackup(fileName, in, size, operator);
            } catch (IOException e) {
                throw new BusinessException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份文件读取失败（" + e.getMessage() + "）");
            }
        } finally {
            deleteQuietly(staging);
        }
    }

    /** 中转文件：dump 全量过验后才经存储域上传，dump 失败不产生半截对象 */
    private static Path createStaging() {
        try {
            return Files.createTempFile("eladmin-backup-", ".sql.gz");
        } catch (IOException e) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：临时文件创建失败（" + e.getMessage() + "）");
        }
    }

    private static void deleteQuietly(Path staging) {
        try {
            Files.deleteIfExists(staging);
        } catch (IOException e) {
            // 中转文件残留不阻断响应：系统临时目录内，不影响账本与对象
        }
    }
}
