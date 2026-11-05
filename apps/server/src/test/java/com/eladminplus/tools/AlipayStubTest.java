package com.eladminplus.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.eladminplus.tools.alipay.AlipayGateway;
import com.eladminplus.tools.alipay.AlipayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 支付宝留位：配置属性类在册且默认值安全（配置缺失不阻断启动）、接口在册且无实现 Bean、无端点暴露。 */
@SpringBootTest
class AlipayStubTest {

    @Autowired
    private AlipayProperties properties;
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void 配置属性类在册且带安全默认值() {
        assertThat(properties).isNotNull();
        assertThat(properties.enabled()).isFalse();
        assertThat(properties.gatewayUrl()).isEqualTo("https://openapi.alipay.com/gateway");
        assertThat(properties.signType()).isEqualTo("RSA2");
    }

    @Test
    void 接口在册且无实现Bean() {
        assertThat(AlipayGateway.class).isInterface();
        assertThat(applicationContext.getBeansOfType(AlipayGateway.class)).isEmpty();
    }

    @Test
    void 无端点暴露() {
        // 路由表中 /tools/alipay 前缀无任何映射（零端点定案）；按名取 MVC 主映射（actuator 另有端点映射）
        var handlerMethods = applicationContext
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
                .getHandlerMethods();
        assertThat(handlerMethods.keySet())
                .noneMatch(info -> String.valueOf(info.getPatternValues()).contains("/tools/alipay"));
    }
}
