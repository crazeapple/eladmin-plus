package com.eladminplus.system.service;

import com.eladminplus.system.domain.TaskLog;
import com.eladminplus.system.mapper.TaskLogMapper;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 执行日志落表：单线程执行器异步写（执行线程不被 IO 阻塞），落表失败只记日志不影响调度。
 */
@Service
public class TaskLogService {

    private static final Logger log = LoggerFactory.getLogger(TaskLogService.class);

    private final TaskLogMapper taskLogMapper;
    private final ExecutorService writer =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "task-log-writer");
                thread.setDaemon(true);
                return thread;
            });

    public TaskLogService(TaskLogMapper taskLogMapper) {
        this.taskLogMapper = taskLogMapper;
    }

    /** 异步落一行执行日志（单线程保序） */
    public void record(TaskLog entry) {
        writer.execute(() -> {
            try {
                taskLogMapper.insert(entry);
            } catch (Exception e) {
                log.error("任务执行日志落表失败：taskId={}", entry.getTaskId(), e);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }
}
