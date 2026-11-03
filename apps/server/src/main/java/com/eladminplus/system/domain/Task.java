package com.eladminplus.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 定时任务定义：目标须在 @TaskMethod 白名单内；status 只存期望态，运行态以 scheduler 为准。 */
@Data
@TableName("task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务名，唯一 */
    private String name;

    /** 目标 Spring Bean 名 */
    private String beanName;

    /** 目标方法名 */
    private String methodName;

    /** 调用参数，单 String 原样传入 */
    private String param;

    /** cron 表达式 */
    private String cron;

    /** 期望态：1 启用 0 暂停 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
