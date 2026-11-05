-- ops 运维域：database_config 连接配置表 + storage_file 扩 source 列（备份文件标记）。
-- 连接密码明文存库（原版同形，内部工具低权连接账号）；列表与详情响应不回显，更新留空保持原值。
-- 备份文件进存储域（P-017 已发布表的内部实现扩展，公开上传契约不变）：source 由服务层内部打标，
-- 空为页面上传 / backup 为数据库备份；备份记录 = storage_file where source=backup 的视图，
-- 有文件即有记录、文件删则记录无，与存储管理页同一真相源。

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

ALTER TABLE storage_file
    ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT '' COMMENT '来源：空为页面上传 / backup 数据库备份' AFTER engine;

CREATE INDEX idx_storage_file_source ON storage_file (source);
