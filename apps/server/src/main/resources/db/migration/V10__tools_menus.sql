-- tools 工具域菜单种子：工具目录 + 存储管理 / 邮件工具 / 支付宝工具三子菜单
-- + 四条按钮权限点行（storage 三点挂存储管理页、email 一点挂邮件工具页）。
-- 支付宝壳页零权限点（后端零实现，页面只呈现提示卡）。
-- 幂等：插入全部 NOT EXISTS 守卫，重复执行安全；permission 标识与后端 @PreAuthorize 一一对应。
-- icon 统一 svg: 前缀入库（侧栏库值优先消费）。

-- 工具目录（首页与演示页 sort 1~3、系统管理 sort 4、监控域 sort 5，工具域殿后 sort 6）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT NULL, 0, 'routes.tools', NULL, 'svg:tools', 0, 6
WHERE NOT EXISTS (SELECT 1
                 FROM menu
                 WHERE i18n_key = 'routes.tools'
                   AND type = 0);

-- 三子菜单（parent 按目录 i18n_key 定位；目录内 sort 1~3）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.toolsStorage', 'tools/storage', 'svg:qiniu', 0, 1
FROM menu p
WHERE p.i18n_key = 'routes.tools'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'tools/storage');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.toolsEmail', 'tools/email', 'svg:email', 0, 2
FROM menu p
WHERE p.i18n_key = 'routes.tools'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'tools/email');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.toolsAliPay', 'tools/ali-pay', 'svg:alipay', 0, 3
FROM menu p
WHERE p.i18n_key = 'routes.tools'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'tools/ali-pay');

-- 存储管理页 3 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'storage:list', 'perms.storageList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'tools/storage'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'storage:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'storage:upload', 'perms.storageUpload', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'tools/storage'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'storage:upload');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'storage:del', 'perms.storageDel', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'tools/storage'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'storage:del');

-- 邮件工具页 1 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'email:send', 'perms.emailSend', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'tools/email'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'email:send');
