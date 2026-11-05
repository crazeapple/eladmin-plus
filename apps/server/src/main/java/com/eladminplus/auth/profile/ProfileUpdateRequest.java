package com.eladminplus.auth.profile;

/** 个人资料更新请求：仅自己可改（会话上下文取用户 id，不接受路径指定他人）。 */
public record ProfileUpdateRequest(String nickName, String email, String phone, Integer gender) {}
