package com.eladminplus.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：反代场景取 X-Forwarded-For 首段（nginx 已注入），
 * 无转发头时落直连地址。取不到（非常规容器环境）返回空串。
 */
public final class ClientIp {

    public static final String FORWARDED_HEADER = "X-Forwarded-For";

    public static final String REAL_IP_HEADER = "X-Real-IP";

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String realIp = request.getHeader(REAL_IP_HEADER);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "" : remoteAddr;
    }
}
