package com.eladminplus.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 客户端 IP 口径：只认 TCP 对端地址（反代解析由容器 RemoteIpValve 承载），转发头一律不采信。 */
class ClientIpTest {

    @Test
    void 直连请求伪造转发头不生效取TCP对端() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "10.1.1.1, 10.1.1.2");
        request.addHeader("X-Real-IP", "10.1.1.3");
        assertThat(ClientIp.resolve(request)).isEqualTo("198.51.100.7");
    }

    @Test
    void 无对端地址返回空串() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(null);
        assertThat(ClientIp.resolve(request)).isEmpty();
    }
}
