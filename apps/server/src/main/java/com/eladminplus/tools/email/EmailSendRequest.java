package com.eladminplus.tools.email;

/** 邮件发送请求：html 为 true 时正文按 HTML 发送；发送不落库，发完即走。 */
public record EmailSendRequest(String to, String subject, String content, Boolean html) {}
