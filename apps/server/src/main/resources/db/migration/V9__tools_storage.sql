-- tools 存储域：storage_file 文件表（原名、存储键、扩展名、大小、MIME、引擎、上传人）+ user 表头像列。
-- 存储键形态 {yyyyMM}/{uuid}.{ext}，uuid 天然不冲突且带全局唯一约束；引擎列记落库时的引擎，
-- 下载按行内引擎字段路由（混引擎历史可读，不受当前配置影响）。头像列存 storage_file 文件 id，
-- 不挂外键：文件删除后残留引用由下载端点按 404 语义兜底。

CREATE TABLE IF NOT EXISTS storage_file
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL COMMENT '原始文件名',
    storage_key VARCHAR(255) NOT NULL COMMENT '存储键（{yyyyMM}/{uuid}.{ext}）',
    ext         VARCHAR(32)  NULL COMMENT '扩展名（小写，无点）',
    size        BIGINT       NOT NULL COMMENT '文件字节数',
    mime        VARCHAR(128) NULL COMMENT 'MIME 类型',
    engine      VARCHAR(16)  NOT NULL COMMENT '存储引擎：local 本地磁盘 / rustfs 对象存储',
    uploader    VARCHAR(64)  NOT NULL COMMENT '上传人用户名',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_storage_file_key UNIQUE (storage_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '存储文件';

CREATE INDEX idx_storage_file_created_at ON storage_file (created_at);
CREATE INDEX idx_storage_file_uploader ON storage_file (uploader);

ALTER TABLE `user`
    ADD COLUMN avatar BIGINT NULL COMMENT '头像文件 id（storage_file）' AFTER gender;
