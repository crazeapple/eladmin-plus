-- system 身份域：部门 / 岗位 / 用户岗位 / 角色部门 / 通用翻译覆盖五新表，
-- user 与 role 扩列（邮箱、电话、性别、状态、部门；角色级别、数据范围）+ 幂等种子
-- （部门树两层级与示例岗位；翻译覆盖表为通用结构，后续域直接复用）

CREATE TABLE IF NOT EXISTS dept
(
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    parent_id  BIGINT      NULL COMMENT '上级部门 id，根为 NULL',
    name       VARCHAR(64) NOT NULL COMMENT '部门名，默认语言；非默认语言走 translation 覆盖',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小者在前',
    enabled    TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_dept_parent FOREIGN KEY (parent_id) REFERENCES dept (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '部门树';

CREATE TABLE IF NOT EXISTS job
(
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(64) NOT NULL COMMENT '岗位名，默认语言；非默认语言走 translation 覆盖',
    sort       INT         NOT NULL DEFAULT 0 COMMENT '排序，小者在前',
    enabled    TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '岗位';

CREATE TABLE IF NOT EXISTS user_job
(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_id  BIGINT NOT NULL,
    CONSTRAINT uk_user_job UNIQUE (user_id, job_id),
    CONSTRAINT fk_user_job_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_job_job FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户岗位关联';

CREATE TABLE IF NOT EXISTS role_dept
(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    dept_id BIGINT NOT NULL,
    CONSTRAINT uk_role_dept UNIQUE (role_id, dept_id),
    CONSTRAINT fk_role_dept_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_dept_dept FOREIGN KEY (dept_id) REFERENCES dept (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色自定义数据范围部门集合';

CREATE TABLE IF NOT EXISTS translation
(
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    entity     VARCHAR(64)  NOT NULL COMMENT '实体名（dept / job / role / app …），小写',
    entity_id  BIGINT       NOT NULL,
    field      VARCHAR(64)  NOT NULL COMMENT '覆盖字段名（如 name）',
    locale     VARCHAR(16)  NOT NULL COMMENT '语言标签（zh-CN / en）',
    value      VARCHAR(255) NOT NULL COMMENT '该语言展示值',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_translation UNIQUE (entity, entity_id, field, locale)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '通用翻译覆盖：主表默认列承载唯一约束与检索，非默认语言展示值在此覆盖';

ALTER TABLE `user`
    ADD COLUMN email   VARCHAR(128) NULL COMMENT '邮箱，唯一' AFTER nick_name,
    ADD COLUMN phone   VARCHAR(32)  NULL COMMENT '联系电话' AFTER email,
    ADD COLUMN gender  TINYINT      NULL COMMENT '性别：0 未知 1 男 2 女' AFTER phone,
    ADD COLUMN enabled TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用 0 停用' AFTER gender,
    ADD COLUMN dept_id BIGINT       NULL COMMENT '所属部门' AFTER enabled,
    ADD CONSTRAINT uk_user_email UNIQUE (email),
    ADD CONSTRAINT fk_user_dept FOREIGN KEY (dept_id) REFERENCES dept (id);

ALTER TABLE role
    ADD COLUMN level      INT         NOT NULL DEFAULT 999 COMMENT '角色级别，越小权限越大' AFTER name,
    ADD COLUMN data_scope VARCHAR(16) NOT NULL DEFAULT 'ALL' COMMENT '数据范围：ALL 全部 / DEPT 本级含子级 / CUSTOM 自定义' AFTER level;

-- —— 幂等种子：已存在即跳过，重复执行安全 ——

INSERT INTO dept (parent_id, name, sort, enabled)
SELECT NULL, '总部', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM dept WHERE parent_id IS NULL AND name = '总部');

INSERT INTO dept (parent_id, name, sort, enabled)
SELECT d.id, '研发部', 1, 1
FROM dept d
WHERE d.parent_id IS NULL
  AND d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM dept c WHERE c.parent_id = d.id AND c.name = '研发部');

INSERT INTO dept (parent_id, name, sort, enabled)
SELECT d.id, '市场部', 2, 1
FROM dept d
WHERE d.parent_id IS NULL
  AND d.name = '总部'
  AND NOT EXISTS (SELECT 1 FROM dept c WHERE c.parent_id = d.id AND c.name = '市场部');

INSERT INTO job (name, sort, enabled)
SELECT '开发工程师', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM job WHERE name = '开发工程师');

-- admin 挂总部（数据权限 DEPT 档演示语义需要）
UPDATE `user` u
SET u.dept_id = (SELECT d.id FROM dept d WHERE d.parent_id IS NULL AND d.name = '总部')
WHERE u.username = 'admin'
  AND u.dept_id IS NULL;
