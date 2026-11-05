package com.eladminplus.ops.dto;

/** 连接配置保存请求：密码更新时留空即保持原值（新增时必填，服务层判定）。 */
public record DatabaseConfigSaveRequest(String host, Integer port, String name, String userName, String password, String remark) {}
