package com.eladminplus.monitor.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 异常日志：全局异常处理器 5xx 分支钩子异步落行，含堆栈文本（超长截断入库）。 */
@Data
@TableName("exception_log")
public class ExceptionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发请求的操作人用户名，无认证态为空 */
    private String username;

    /** 触发请求 IP */
    private String ip;

    /** 异常类名 */
    private String name;

    /** 异常消息，超长截断 */
    private String message;

    /** 堆栈文本，超长截断入库 */
    private String stackTrace;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
