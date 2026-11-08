-- 菜单图标源替换：svg: 库值按选型语义映射为 nv: ionicons Outline 名（逐名核对在册，
-- 杜绝渲染落空值入库）。ep: 值（box / refresh-left）与映射表外的 svg: 值不猜、留原样
-- （现状库无后者形态）。
UPDATE menu
SET icon = CASE icon
    WHEN 'svg:index' THEN 'nv:home-outline'
    WHEN 'svg:user' THEN 'nv:person-outline'
    WHEN 'svg:role' THEN 'nv:person-circle-outline'
    WHEN 'svg:dept' THEN 'nv:business-outline'
    WHEN 'svg:people' THEN 'nv:people-outline'
    WHEN 'svg:peoples' THEN 'nv:people-circle-outline'
    WHEN 'svg:menu' THEN 'nv:menu-outline'
    WHEN 'svg:dictionary' THEN 'nv:library-outline'
    WHEN 'svg:timing' THEN 'nv:time-outline'
    WHEN 'svg:monitor' THEN 'nv:speedometer-outline'
    WHEN 'svg:log' THEN 'nv:document-text-outline'
    WHEN 'svg:error' THEN 'nv:warning-outline'
    WHEN 'svg:server' THEN 'nv:server-outline'
    WHEN 'svg:system' THEN 'nv:cog-outline'
    WHEN 'svg:tools' THEN 'nv:construct-outline'
    WHEN 'svg:database' THEN 'nv:albums-outline'
    WHEN 'svg:deploy' THEN 'nv:rocket-outline'
    WHEN 'svg:email' THEN 'nv:mail-outline'
    WHEN 'svg:qiniu' THEN 'nv:cloud-outline'
    WHEN 'svg:alipay' THEN 'nv:wallet-outline'
    WHEN 'svg:develop' THEN 'nv:code-slash-outline'
    ELSE icon
END
WHERE icon LIKE 'svg:%';
