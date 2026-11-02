-- RBAC 五表（用户 / 角色 / 用户角色 / 菜单 / 角色菜单）+ 幂等种子
-- （root 账号、ROLE_ROOT 内置角色位、初始菜单：首页与演示页）

CREATE TABLE IF NOT EXISTS `user`
(
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL COMMENT '登录名，唯一',
    password   VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希',
    nick_name  VARCHAR(64)  NULL COMMENT '昵称（单语言）',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_username UNIQUE (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户';

CREATE TABLE IF NOT EXISTS role
(
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    code       VARCHAR(64) NOT NULL COMMENT '角色码，唯一；ROLE_ROOT 为内置超管角色位',
    name       VARCHAR(64) NOT NULL COMMENT '角色名',
    built_in   TINYINT     NOT NULL DEFAULT 0 COMMENT '内置角色不可删除',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_role_code UNIQUE (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色';

CREATE TABLE IF NOT EXISTS user_role
(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT uk_user_role UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES `user` (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户角色关联';

CREATE TABLE IF NOT EXISTS menu
(
    id                 BIGINT       AUTO_INCREMENT PRIMARY KEY,
    parent_id          BIGINT       NULL COMMENT '父菜单 id，根为 NULL',
    type               TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：0 目录 1 菜单 2 按钮',
    permission         VARCHAR(128) NULL COMMENT '按钮型权限点（module:entity:action）',
    i18n_key           VARCHAR(128) NULL COMMENT '展示文案 i18n key（目录 / 菜单）',
    component_path_key VARCHAR(128) NULL COMMENT '前端组件路径 key（views 下相对路径，不含扩展名）',
    no_cache           TINYINT      NOT NULL DEFAULT 0 COMMENT '页面不入 keep-alive 缓存',
    sort               INT          NOT NULL DEFAULT 0 COMMENT '排序，小者在前',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '菜单树';

CREATE TABLE IF NOT EXISTS role_menu
(
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    CONSTRAINT uk_role_menu UNIQUE (role_id, menu_id),
    CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES role (id),
    CONSTRAINT fk_role_menu_menu FOREIGN KEY (menu_id) REFERENCES menu (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '角色菜单关联';

-- —— 幂等种子：已存在即跳过，重复执行安全 ——

-- root 账号（用户名 admin，初始密码 123456 的 BCrypt 哈希，首登后应改密）
INSERT INTO `user` (username, password, nick_name)
SELECT 'admin', '$2a$10$BU4TAC2D4GsNT5r1.k89denf2KvR7JLv7jL9i8G9IaIAV5Z8mX8Jq', '系统管理员'
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'admin');

-- ROLE_ROOT 内置角色位（超管判定走角色位，不依赖用户名）
INSERT INTO role (code, name, built_in)
SELECT 'ROLE_ROOT', '内置超级管理员', 1
WHERE NOT EXISTS (SELECT 1 FROM role WHERE code = 'ROLE_ROOT');

-- admin → ROLE_ROOT
INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM `user` u,
     role r
WHERE u.username = 'admin'
  AND r.code = 'ROLE_ROOT'
  AND NOT EXISTS(SELECT 1
                 FROM user_role ur
                 WHERE ur.user_id = u.id
                   AND ur.role_id = r.id);

-- 初始菜单：首页与两个演示页（component_path_key 对齐前端 views 目录，i18n_key 复用现有路由文案 key）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT NULL, 1, 'routes.home', 'home/index', 0, 1
WHERE NOT EXISTS(SELECT 1 FROM menu WHERE component_path_key = 'home/index');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT NULL, 1, 'routes.demoCached', 'demo/cached', 0, 2
WHERE NOT EXISTS(SELECT 1 FROM menu WHERE component_path_key = 'demo/cached');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT NULL, 1, 'routes.demoNoCache', 'demo/no-cache', 1, 3
WHERE NOT EXISTS(SELECT 1 FROM menu WHERE component_path_key = 'demo/no-cache');
