package com.eladminplus.common.error;

import org.springframework.http.HttpStatus;

/**
 * 受控异常基类：携带语义化 HTTP 状态，消息可经 MessageSource 解析（默认 locale 直出）。
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
