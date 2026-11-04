package com.eladminplus.monitor.task;

import com.eladminplus.monitor.service.LogCleanupService;
import com.eladminplus.system.task.TaskMethod;
import org.springframework.stereotype.Component;

/**
 * 日志轮转任务：V7 预注册行（bean=logCleanupJob、method=clean）对应白名单目标，
 * 任务页可常规管理（可暂停可删除）；运行期删除不自动回补，恢复依赖任务页重新创建。
 */
@Component
public class LogCleanupJob {

    private final LogCleanupService logCleanupService;

    public LogCleanupJob(LogCleanupService logCleanupService) {
        this.logCleanupService = logCleanupService;
    }

    @TaskMethod("按 monitor.log.retention-days 保留天数清理操作与异常日志超期行（参数不用）")
    public void clean() {
        logCleanupService.clean();
    }
}
