package com.eladminplus.common.error;

import java.time.Instant;

/**
 * 统一错误响应体：timestamp 为 ISO-8601；message 不含堆栈（堆栈只进日志）。
 */
public record ErrorBody(int status, Instant timestamp, String message) {
}
