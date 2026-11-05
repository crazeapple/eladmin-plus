package com.eladminplus.auth.profile;

/** 改密请求：新旧密码明文随请求体（与登录同走 TLS 通道；管理端重置密码同此形态）。 */
public record PasswordChangeRequest(String oldPassword, String newPassword) {}
