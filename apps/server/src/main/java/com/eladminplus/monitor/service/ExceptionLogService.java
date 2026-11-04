package com.eladminplus.monitor.service;

import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.log.LogAsyncConfig;
import com.eladminplus.monitor.mapper.ExceptionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异常日志域服务：全局异常处理器 5xx 分支钩子的异步落表入口；
 * 查询与维护端点随本域提案同批交付。
 */
@Service
public class ExceptionLogService {

    private static final Logger log = LoggerFactory.getLogger(ExceptionLogService.class);

    private final ExceptionLogMapper exceptionLogMapper;

    public ExceptionLogService(ExceptionLogMapper exceptionLogMapper) {
        this.exceptionLogMapper = exceptionLogMapper;
    }

    /** 异步落一行异常日志（落表失败只记日志，不影响错误响应） */
    @Async(LogAsyncConfig.LOG_WRITER_EXECUTOR)
    public void record(ExceptionLog entry) {
        try {
            exceptionLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("异常日志落表失败：name={}", entry.getName(), e);
        }
    }
}
