package com.eladminplus.ops.service;

import com.eladminplus.common.error.BusinessException;
import com.eladminplus.ops.OpsBackupProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 备份执行器：docker exec 进 mysql 容器调 mysqldump --databases（输出含 CREATE DATABASE），
 * 应用进程捕获 stdout 流直写目标文件（容器内不落盘）。密码经 MYSQL_PWD 环境变量传入（与 P-008
 * 备份脚本同源惯例，不进 mysqldump 参数）。docker CLI 不可用、容器不在、mysqldump 出错、凭据错误
 * 都归「备份执行失败」5xx 含原因。
 */
@Component
public class MysqlDumpExecutor {

    /** 失败报错携带的 stderr 尾段长度上限（报错可读且不无限长） */
    private static final int STDERR_TAIL_MAX = 500;

    private final OpsBackupProperties properties;

    public MysqlDumpExecutor(OpsBackupProperties properties) {
        this.properties = properties;
    }

    /** 执行导出：stdout 流直写 target；非零退出按执行失败 5xx 拒绝（stderr 尾段作原因） */
    public void dump(String database, String userName, String password, Path target) {
        Process process = start(database, userName, password);
        // stderr 旁路并发排空，防管道缓冲写满阻塞进程
        StringBuilder stderr = new StringBuilder();
        Thread drain = Thread.ofPlatform().daemon().start(() -> drain(process, stderr));
        try (OutputStream out = Files.newOutputStream(target)) {
            process.getInputStream().transferTo(out);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：备份文件写入中断（" + e.getMessage() + "）");
        }
        waitForExit(process, stderr, drain);
    }

    private Process start(String database, String userName, String password) {
        ProcessBuilder builder = new ProcessBuilder(
                "docker",
                "exec",
                "-e",
                "MYSQL_PWD=" + password,
                properties.container(),
                "mysqldump",
                "--databases",
                database,
                "-u" + userName);
        try {
            return builder.start();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：docker 命令不可用（" + e.getMessage() + "）");
        }
    }

    private void waitForExit(Process process, StringBuilder stderr, Thread drain) {
        int code;
        try {
            code = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "备份执行失败：执行被中断");
        }
        try {
            drain.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (code != 0) {
            throw new BusinessException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "备份执行失败：mysqldump 退出码 " + code + "（" + tail(stderr.toString()) + "）");
        }
    }

    private static void drain(Process process, StringBuilder stderr) {
        try (InputStream in = process.getErrorStream()) {
            stderr.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            // 排空失败不阻断主流程：失败判定以退出码为准
        }
    }

    private static String tail(String text) {
        String trimmed = text.trim();
        return trimmed.length() <= STDERR_TAIL_MAX ? trimmed : trimmed.substring(trimmed.length() - STDERR_TAIL_MAX);
    }
}
