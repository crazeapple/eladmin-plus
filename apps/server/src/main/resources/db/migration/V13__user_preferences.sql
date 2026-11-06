-- 用户级偏好：user 加 preferences JSON 列（theme / primaryColor / locale / iconSet 四项整体存取）。
-- NULL 缺省 = 未设置过偏好，服务层按缺省返回（theme=auto、primaryColor=品牌靛蓝、locale=system、iconSet=svg）；
-- 内容损坏同缺省处理不 500。读随 me 响应带出，写走 PUT /profile/preferences 全量幂等。

ALTER TABLE `user`
    ADD COLUMN preferences JSON NULL COMMENT '用户级偏好 JSON（theme / primaryColor / locale / iconSet），NULL 即缺省' AFTER avatar;
