-- monitor 监控域菜单种子：监控管理目录 + 操作日志 / 异常日志 / 在线用户 / 服务监控四子菜单
-- + 五条按钮权限点行（log 两点挂操作日志页、online 两点挂在线用户页、monitor 一点挂服务监控页）。
-- 异常日志页与操作日志页共用 log 组权限点，不重复挂（同名点重复入册只会让授权界面出重复行）。
-- 幂等：插入全部 NOT EXISTS 守卫，重复执行安全；permission 标识与后端 @PreAuthorize 一一对应。
-- icon 统一 svg: 前缀入库（侧栏库值优先消费）。

-- 监控管理目录（首页与演示页 sort 1~3、系统管理 sort 4，监控域殿后 sort 5）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT NULL, 0, 'routes.monitor', NULL, 'svg:monitor', 0, 5
WHERE NOT EXISTS (SELECT 1
                 FROM menu
                 WHERE i18n_key = 'routes.monitor'
                   AND type = 0);

-- 四子菜单（parent 按目录 i18n_key 定位；目录内 sort 1~4）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.monitorOperationLogs', 'monitor/operation-logs', 'svg:log', 0, 1
FROM menu p
WHERE p.i18n_key = 'routes.monitor'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'monitor/operation-logs');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.monitorExceptionLogs', 'monitor/exception-logs', 'svg:error', 0, 2
FROM menu p
WHERE p.i18n_key = 'routes.monitor'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'monitor/exception-logs');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.monitorOnlineUsers', 'monitor/online-users', 'svg:peoples', 0, 3
FROM menu p
WHERE p.i18n_key = 'routes.monitor'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'monitor/online-users');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.monitorServerInfo', 'monitor/server-info', 'svg:server', 0, 4
FROM menu p
WHERE p.i18n_key = 'routes.monitor'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'monitor/server-info');

-- 操作日志页 2 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'monitor:log:list', 'perms.monitorLogList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'monitor/operation-logs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'monitor:log:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'monitor:log:del', 'perms.monitorLogDel', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'monitor/operation-logs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'monitor:log:del');

-- 在线用户页 2 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'monitor:online:list', 'perms.monitorOnlineList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'monitor/online-users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'monitor:online:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'monitor:online:del', 'perms.monitorOnlineDel', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'monitor/online-users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'monitor:online:del');

-- 服务监控页 1 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'monitor:monitor:list', 'perms.monitorMonitorList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'monitor/server-info'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'monitor:monitor:list');
