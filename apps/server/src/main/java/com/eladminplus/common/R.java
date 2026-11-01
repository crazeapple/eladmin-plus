package com.eladminplus.common;

import org.springframework.http.HttpStatus;

/**
 * 全链统一响应 envelope：成功与失败同形。
 * code 取 HTTP 状态码，与状态行同值；message 成功为空串、失败为可直接展示的本地化消息；
 * data 为业务负载，无返回体的写操作统一 200 且 data 为 null。
 */
public record R<T>(int code, String message, T data) {

    public static <T> R<T> ok(T data) {
        return new R<>(HttpStatus.OK.value(), "", data);
    }

    public static R<Void> ok() {
        return new R<>(HttpStatus.OK.value(), "", null);
    }

    public static R<Void> error(HttpStatus status, String message) {
        return new R<>(status.value(), message, null);
    }
}
