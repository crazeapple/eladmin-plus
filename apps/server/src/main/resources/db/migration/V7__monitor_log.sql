-- monitor 监控域：操作日志与异常日志两表（用户拍板分离建表，操作行不背堆栈列）
-- + 日志清理任务预注册行（@TaskMethod 白名单 logCleanupJob#clean，cron 每天凌晨、状态正常）。
-- 任务页可常规管理（可暂停可删除）；行幂等 NOT EXISTS 守卫，迁移执行时生效，
-- 运行期删除不自动回补，恢复依赖任务页重新创建。轮转按 created_at 过滤，两表建轮转索引。

CREATE TABLE IF NOT EXISTS operation_log
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NULL COMMENT '操作人用户名',
    ip            VARCHAR(64)  NULL COMMENT '操作 IP',
    description   VARCHAR(255) NULL COMMENT '操作描述（@Log 标记文案）',
    elapsed_ms    BIGINT       NULL COMMENT '耗时毫秒',
    request_param TEXT         NULL COMMENT '请求参数 JSON，password 类字段值掩码；非 JSON 请求体落描述性占位',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '操作日志';

CREATE TABLE IF NOT EXISTS exception_log
(
    id          BIGINT        AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(64)   NULL COMMENT '触发请求的操作人用户名，无认证态为空',
    ip          VARCHAR(64)   NULL COMMENT '触发请求 IP',
    name        VARCHAR(128)  NOT NULL COMMENT '异常类名',
    message     VARCHAR(512)  NULL COMMENT '异常消息，超长截断',
    stack_trace TEXT          NULL COMMENT '堆栈文本，超长截断入库',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '异常日志';

CREATE INDEX idx_operation_log_created_at ON operation_log (created_at);
CREATE INDEX idx_operation_log_username ON operation_log (username);
CREATE INDEX idx_exception_log_created_at ON exception_log (created_at);
CREATE INDEX idx_exception_log_username ON exception_log (username);

-- 日志清理任务预注册行（cron 每天凌晨 3 点）
INSERT INTO task (name, bean_name, method_name, param, cron, status)
SELECT '日志清理', 'logCleanupJob', 'clean', NULL, '0 0 3 * * ?', 1
WHERE NOT EXISTS (SELECT 1 FROM task WHERE name = '日志清理');
