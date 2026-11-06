-- 演示菜单目录化：原「演示·缓存 / 演示·不缓存」两个一级菜单（sort 2、3）混在业务菜单中间，
-- 改建一级目录「演示」殿后（sort 取一级最大 sort+1），两子菜单挂入其下（目录内 sort 1、2）。
-- 页面路由与组件路径不动（demo/cached、demo/no-cache），keep-alive 缓存语义不变。
-- 幂等：目录 INSERT 以 NOT EXISTS（i18n_key=routes.demo 且 type=0）守卫；
-- 子菜单挂接为 UPDATE 按 i18n_key 定位，重复执行安全，管理员手改挂接的行一并收编回目录。

-- 演示目录（殿后；本库种子序一级最大 sort 7，目录落 sort 8）
INSERT INTO menu (parent_id, type, i18n_key, component_path_key, icon, no_cache, sort)
SELECT NULL, 0, 'routes.demo', NULL, 'svg:develop', 0, mx.next_sort
FROM (SELECT COALESCE(MAX(sort), 0) + 1 AS next_sort
      FROM menu
      WHERE parent_id IS NULL) mx
WHERE NOT EXISTS (SELECT 1
                 FROM menu
                 WHERE i18n_key = 'routes.demo'
                   AND type = 0);

-- 演示缓存挂入目录（icon 库值 V15 已在册 ep:box，不动）
UPDATE menu c
JOIN menu p ON p.i18n_key = 'routes.demo' AND p.type = 0
SET c.parent_id = p.id,
    c.sort = 1
WHERE c.i18n_key = 'routes.demoCached'
  AND c.type = 1;

-- 演示不缓存挂入目录（icon 库值 V14 已在册 ep:refresh-left，不动）
UPDATE menu c
JOIN menu p ON p.i18n_key = 'routes.demo' AND p.type = 0
SET c.parent_id = p.id,
    c.sort = 2
WHERE c.i18n_key = 'routes.demoNoCache'
  AND c.type = 1;
