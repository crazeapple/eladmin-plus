-- ops 运维域：database_config 连接配置表 + backup_record 备份记录表。
-- 连接密码明文存库（原版同形，内部工具低权连接账号）；列表与详情响应不回显，更新留空保持原值。
-- 备份记录是成功备份账本（失败不落行，响应即报错），与落盘文件一一对应：删记录同删文件，
-- 文件已失仅删记录；连接配置删除不动备份记录（历史事实不受配置删除影响）。

CREATE TABLE IF NOT EXISTS database_config
(
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    host       VARCHAR(255) NOT NULL COMMENT '主机地址',
    port       INT          NOT NULL COMMENT '端口',
    name       VARCHAR(255) NOT NULL COMMENT '库名',
    user_name  VARCHAR(255) NOT NULL COMMENT '连接账号',
    password   VARCHAR(255) NOT NULL COMMENT '连接密码（明文存库，响应不回显）',
    remark     VARCHAR(255) NULL COMMENT '备注',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '数据库连接配置';

CREATE TABLE IF NOT EXISTS backup_record
(
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    file_name  VARCHAR(255) NOT NULL COMMENT '备份文件名（{库名}_{yyyyMMddHHmmss}.sql）',
    dir        VARCHAR(512) NOT NULL COMMENT '备份目录',
    db_name    VARCHAR(255) NOT NULL COMMENT '库名',
    size       BIGINT       NOT NULL COMMENT '文件字节数',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '备份记录';
