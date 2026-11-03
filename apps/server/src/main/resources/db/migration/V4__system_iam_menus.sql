-- 身份域菜单种子：系统管理目录 + 用户/角色/部门/岗位四子菜单 + 按钮权限点行。
-- 幂等：全部 NOT EXISTS 守卫，重复执行安全。
-- 权限点行使角色授权树含按钮节点（授权 → role_menu → authorities 链闭环），
-- permission 标识与后端 @PreAuthorize 一一对应。

-- 系统管理目录（首页与演示页 sort 1~3，系统管理殿后）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT NULL, 0, 'routes.system', NULL, 0, 4
WHERE NOT EXISTS (SELECT 1
                 FROM menu
                 WHERE i18n_key = 'routes.system'
                   AND type = 0);

-- 四子菜单（原版序：用户、角色、部门、岗位；parent 按目录 i18n_key 定位）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 1, 'routes.systemUsers', 'system/users', 0, 1
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/users');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 1, 'routes.systemRoles', 'system/roles', 0, 2
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/roles');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 1, 'routes.systemDepts', 'system/depts', 0, 3
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/depts');

INSERT INTO menu (parent_id, type, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 1, 'routes.systemJobs', 'system/jobs', 0, 4
FROM menu p
WHERE p.i18n_key = 'routes.system'
  AND p.type = 0
  AND NOT EXISTS (SELECT 1 FROM menu WHERE component_path_key = 'system/jobs');

-- 按钮权限点行：部门 4 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dept:list', 'perms.systemDeptList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/depts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dept:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dept:add', 'perms.systemDeptAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/depts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dept:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dept:edit', 'perms.systemDeptEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/depts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dept:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:dept:del', 'perms.systemDeptDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/depts'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:dept:del');

-- 岗位 4 点
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:job:list', 'perms.systemJobList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/jobs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:job:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:job:add', 'perms.systemJobAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/jobs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:job:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:job:edit', 'perms.systemJobEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/jobs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:job:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:job:del', 'perms.systemJobDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/jobs'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:job:del');

-- 用户 5 点（含重置密码）
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:user:list', 'perms.systemUserList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:user:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:user:add', 'perms.systemUserAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:user:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:user:edit', 'perms.systemUserEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:user:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:user:resetPwd', 'perms.systemUserResetPwd', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:user:resetPwd');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:user:del', 'perms.systemUserDel', NULL, 0, 5
FROM menu p
WHERE p.component_path_key = 'system/users'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:user:del');

-- 角色 4 点 + 菜单树查询 1 点（授权页消费全量菜单树，权限点挂角色页）
INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:role:list', 'perms.systemRoleList', NULL, 0, 1
FROM menu p
WHERE p.component_path_key = 'system/roles'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:role:list');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:role:add', 'perms.systemRoleAdd', NULL, 0, 2
FROM menu p
WHERE p.component_path_key = 'system/roles'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:role:add');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:role:edit', 'perms.systemRoleEdit', NULL, 0, 3
FROM menu p
WHERE p.component_path_key = 'system/roles'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:role:edit');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:role:del', 'perms.systemRoleDel', NULL, 0, 4
FROM menu p
WHERE p.component_path_key = 'system/roles'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:role:del');

INSERT INTO menu (parent_id, type, permission, i18n_key, component_path_key, no_cache, sort)
SELECT p.id, 2, 'system:menu:list', 'perms.systemMenuList', NULL, 0, 5
FROM menu p
WHERE p.component_path_key = 'system/roles'
  AND NOT EXISTS (SELECT 1 FROM menu m WHERE m.permission = 'system:menu:list');
