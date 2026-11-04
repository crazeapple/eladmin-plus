package com.eladminplus.system.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 演示任务：白名单清单非空的前置（任务表单目标下拉、执行与日志链路演示用）。 */
@Component
public class SampleJob {

    private static final Logger log = LoggerFactory.getLogger(SampleJob.class);

    @TaskMethod("演示参数，原样打印到日志")
    public void run(String param) {
        log.info("演示任务执行：param={}", param);
    }
}
