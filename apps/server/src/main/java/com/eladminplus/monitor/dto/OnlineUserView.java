package com.eladminplus.monitor.dto;

/**
 * 在线用户行：会话索引枚举所得，五字段（昵称、用户名、IP、登录时间、浏览器）+ userId（强退定位用）。
 * 同一用户多端登录多行并列；浏览器为 UA 原样文本（解析与图标展示策略归前端）。
 */
public record OnlineUserView(
        Long userId, String nickName, String username, String ip, String loginAt, String browser) {}
