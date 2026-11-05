package com.eladminplus.ops.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 数据库连接配置：备份执行的连接凭据来源；密码明文存库（原版同形），响应一律经视图脱敏。 */
@Data
@TableName("database_config")
public class DatabaseConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主机地址 */
    private String host;

    /** 端口 */
    private Integer port;

    /** 库名 */
    private String name;

    /** 连接账号 */
    private String userName;

    /** 连接密码（明文存库，响应不回显） */
    private String password;

    /** 备注 */
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
