package com.eladminplus.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：直取 TCP 对端地址。反代场景由容器 RemoteIpValve 预先解析
 * （server.forward-headers-strategy=native，仅可信内网段采信 X-Forwarded-For），
 * 此处读到的对端地址已是解析结果——直连请求自带伪造头不生效，取的即真实对端。
 * 取不到（非常规容器环境）返回空串。
 */
public final class ClientIp {

    private ClientIp() {}

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "" : remoteAddr;
    }
}
