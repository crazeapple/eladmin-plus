-- system 配置域菜单种子：菜单管理 / 字典管理 / 定时任务三子菜单 + 各页按钮权限点行。
-- 幂等：插入全部 NOT EXISTS 守卫，重复执行安全；共用权限点 system:menu:list
-- （菜单树端点被角色授权页与管理页共用）既有行由角色页改挂菜单页首行，
-- UPDATE 天然幂等，存量角色授权按行 id 记账不受改挂影响。
-- icon 统一 svg: 前缀入库（侧栏库值优先消费）。

-- 三子菜单（身份域四页 sort 1~4，配置域三页殿后 5~7）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.systemMenus', 'system/menus', 'svg:menu', 0, 5
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/menus');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.systemDicts', 'system/dicts', 'svg:dictionary', 0, 6
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/dicts');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT p.id, 1, 'routes.systemTasks', 'system/tasks', 'svg:timing', 0, 7
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/tasks');

-- 菜单页按钮点：新增 / 编辑 / 删除（list 为共用点，见文末改挂）
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:menu:add', 'perms.systemMenuAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/menus'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:menu:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:menu:edit', 'perms.systemMenuEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/menus'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:menu:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:menu:del', 'perms.systemMenuDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/menus'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:menu:del');

-- 字典页 4 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dict:list', 'perms.systemDictList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/dicts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dict:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dict:add', 'perms.systemDictAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/dicts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dict:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dict:edit', 'perms.systemDictEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/dicts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dict:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dict:del', 'perms.systemDictDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/dicts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dict:del');

-- 任务页 4 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:task:list', 'perms.systemTaskList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/tasks'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:task:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:task:add', 'perms.systemTaskAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/tasks'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:task:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:task:edit', 'perms.systemTaskEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/tasks'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:task:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:task:del', 'perms.systemTaskDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/tasks'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:task:del');

-- 共用菜单树查询点改挂菜单页首行（派生表包一层规避 MySQL 同表更新限定）
UPDATE menu
SET parent_id = (SELECT id FROM (SELECT id FROM menu WHERE component_path_key = 'system/menus') t),
    sort = 1
WHERE permission = 'system:menu:list';
