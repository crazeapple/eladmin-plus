-- ops 运维域菜单种子：运维目录 + 数据库管理单子菜单
-- + 五条按钮权限点行（database 五点全挂数据库管理页，列表与记录视图共用 list / del 点）。
-- 幂等：插入全部 NOT EXISTS 守卫，重复执行安全；permission 标识与后端 @PreAuthorize 一一对应。
-- icon 统一 svg: 前缀入库（侧栏库值优先消费）。

-- 运维目录（首页与演示页 sort 1~3、系统管理 sort 4、监控域 sort 5、工具域 sort 6，运维域殿后 sort 7）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT NULL, 0, 'routes.ops', NULL, 'svg:deploy', 0, 7
WHERE NOT EXISTS (SELECT 1
                 FROM menu
                 WHERE i18n_key = 'routes.ops'
                   AND type = 0);

-- 数据库管理子菜单（一页两区块：连接配置 / 备份记录，单子菜单对应单页）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.database', 'database', 'svg:database', 0, 1
FROM menu p
WHERE p.i18n_key = 'routes.ops'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'database');

-- 数据库管理页 5 点（备份记录行级删除挂 del 点、导出挂 backup 点，与端点授权面一致）
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'database:list', 'perms.databaseList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'database'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'database:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'database:add', 'perms.databaseAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'database'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'database:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'database:edit', 'perms.databaseEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'database'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'database:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'database:del', 'perms.databaseDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'database'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'database:del');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'database:backup', 'perms.databaseBackup', NULL, 0, 5
FROM menu p
WHERE p.component_path_key = 'database'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'database:backup');
