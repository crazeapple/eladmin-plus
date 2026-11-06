-- 演示缓存菜单图标换 EP 集：svg:tab 图形视觉不理想（且曾因缺 viewBox 渲染残块），
-- 改 ep:box 贴「缓存存储」语义。仅覆盖仍为 svg:tab 的行，管理员已手改的不回写。
UPDATE menu SET icon = 'ep:box' WHERE i18n_key = 'routes.demoCached' AND type = 1 AND icon = 'svg:tab';
