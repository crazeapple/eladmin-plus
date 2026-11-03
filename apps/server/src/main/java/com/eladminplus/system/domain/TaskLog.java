package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 定时任务执行日志：一次执行一行（异步落表），成功与失败都留痕。 */
@Data
@TableName("task_log")
public class TaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务 id */
    private Long taskId;

    /** 本次执行开始时间 */
    private LocalDateTime executeTime;

    /** 执行结果：1 成功 0 失败 */
    private Integer success;

    /** 耗时毫秒 */
    private Long durationMs;

    /** 异常信息摘要 */
    private String errorMsg;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
