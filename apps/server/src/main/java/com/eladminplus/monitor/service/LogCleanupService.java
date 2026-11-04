package com.eladminplus.monitor.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.monitor.domain.ExceptionLog;
import com.eladminplus.monitor.domain.OperationLog;
import com.eladminplus.monitor.mapper.ExceptionLogMapper;
import com.eladminplus.monitor.mapper.OperationLogMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 日志自动轮转：按保留天数删除操作与异常两表超期行（以行创建时间为准）。
 * 保留天数读 monitor.log.retention-days（默认 30）；在执行时解析——
 * 非正整数视为非法，抛错交 Quartz 落 task_log 失败记录，不误删数据。
 */
@Service
public class LogCleanupService {

    private static final Logger log = LoggerFactory.getLogger(LogCleanupService.class);

    private final OperationLogMapper operationLogMapper;
    private final ExceptionLogMapper exceptionLogMapper;
    /** 原始配置值（字符串留形态，执行时解析，非法值不阻断应用启动） */
    private final String retentionDaysRaw;

    public LogCleanupService(
            OperationLogMapper operationLogMapper,
            ExceptionLogMapper exceptionLogMapper,
            @Value("${monitor.log.retention-days:30}") String retentionDaysRaw) {
        this.operationLogMapper = operationLogMapper;
        this.exceptionLogMapper = exceptionLogMapper;
        this.retentionDaysRaw = retentionDaysRaw;
    }

    /** 执行一轮清理：删两表创建时间早于阈值的行，返回语义仅为日志 */
    public void clean() {
        int days = retentionDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int operations = operationLogMapper.delete(Wrappers.<OperationLog>lambdaQuery()
                .lt(OperationLog::getCreatedAt, threshold));
        int exceptions = exceptionLogMapper.delete(Wrappers.<ExceptionLog>lambdaQuery()
                .lt(ExceptionLog::getCreatedAt, threshold));
        log.info("日志轮转完成：保留 {} 天，清理操作日志 {} 行、异常日志 {} 行", days, operations, exceptions);
    }

    /** 保留天数解析：非数字、0 与负数均视为非法，抛错由调用方落失败记录 */
    private int retentionDays() {
        try {
            int days = Integer.parseInt(retentionDaysRaw.trim());
            if (days > 0) {
                return days;
            }
        } catch (NumberFormatException | NullPointerException e) {
            // 缺配或非数字统一走下方非法抛错
        }
        throw new IllegalStateException("保留天数配置非法（monitor.log.retention-days=" + retentionDaysRaw + "），不执行清理");
    }
}
