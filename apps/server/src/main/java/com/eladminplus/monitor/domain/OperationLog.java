package com.eladminplus.monitor.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 操作日志：@Log 标注端点被调用后异步落行，五字段（操作人、IP、描述、耗时、请求参数）。 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人用户名 */
    private String username;

    /** 操作 IP */
    private String ip;

    /** 操作描述（@Log 标记文案） */
    private String description;

    /** 耗时毫秒 */
    private Long elapsedMs;

    /** 请求参数 JSON，password 类字段值掩码；非 JSON 请求体落描述性占位 */
    private String requestParam;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
