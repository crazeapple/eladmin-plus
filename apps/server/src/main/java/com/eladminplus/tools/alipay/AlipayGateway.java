package com.eladminplus.tools.alipay;

/**
 * 支付宝网关接口：集成留位（零实现零端点）——真实集成需企业资质，接口形态给 tools 前端壳页留锚。
 * 当前上下文无实现 Bean 属预期形态；真实集成落地时注册实现 Bean 即可注入消费。
 */
public interface AlipayGateway {

    /** 统一下单：返回支付凭证串（跳转 / 二维码消费），留给真实集成实现 */
    String prepay(PayOrder order);

    /** 关单：按商户订单号关闭未支付单，留给真实集成实现 */
    void close(String outTradeNo);

    /** 单笔下单请求：金额以分计，notifyUrl 为异步通知回调地址 */
    record PayOrder(String outTradeNo, String subject, long amountFen, String notifyUrl) {}
}
