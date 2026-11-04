package com.eladminplus.monitor.service;

import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.log.LogAsyncConfig;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志域服务：@Log 切面的异步落表入口；查询与维护端点随本域提案同批交付。
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /** 异步落一行操作日志（日志是旁路关注，落表失败只记日志不影响业务响应） */
    @Async(LogAsyncConfig.LOG_WRITER_EXECUTOR)
    public void record(OperationLog entry) {
        try {
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("操作日志落表失败：description={}", entry.getDescription(), e);
        }
    }
}
