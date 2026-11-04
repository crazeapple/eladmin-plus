package com.eladminplus.monitor.log;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 日志落库异步化：专用单线程执行器保序旁路写（业务线程不被 IO 阻塞，
 * 落表失败只记日志不影响业务响应）；停机时等待存量落库完成再收口。
 */
@Configuration
@EnableAsync
public class LogAsyncConfig {

    public static final String LOG_WRITER_EXECUTOR = "logWriterExecutor";

    @Bean(LOG_WRITER_EXECUTOR)
    public ThreadPoolTaskExecutor logWriterExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("log-writer");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationMillis(5_000);
        return executor;
    }
}
