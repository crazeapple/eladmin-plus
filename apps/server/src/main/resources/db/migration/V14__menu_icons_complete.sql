-- 菜单图标补齐：存量 8 个 icon NULL 菜单补库值（前端静态映射兜底表随数据补齐退役）。
-- 语义贴切度复查择优：首页沿用 index，页签缓存沿用 tab，系统管理域沿用 system / user / role /
-- dept / people；演示·不缓存原兜底 edit 与「状态不保留」语义不贴切，改 EP 集 refresh-left
-- （刷新即失，无状态页的直白表意）。
-- 幂等：只补 NULL 行，管理员已自设图标的不动，重复执行安全。

UPDATE menu SET icon = 'svg:index' WHERE i18n_key = 'routes.home' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'svg:tab' WHERE i18n_key = 'routes.demoCached' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'ep:refresh-left'
WHERE i18n_key = 'routes.demoNoCache' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'svg:system' WHERE i18n_key = 'routes.system' AND type = 0 AND icon IS NULL;

UPDATE menu SET icon = 'svg:user' WHERE i18n_key = 'routes.systemUsers' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'svg:role' WHERE i18n_key = 'routes.systemRoles' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'svg:dept' WHERE i18n_key = 'routes.systemDepts' AND type = 1 AND icon IS NULL;

UPDATE menu SET icon = 'svg:people' WHERE i18n_key = 'routes.systemJobs' AND type = 1 AND icon IS NULL;
