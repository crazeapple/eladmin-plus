package com.eladminplus.monitor.dto;

import com.eladminplus.common.error.BusinessException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/**
 * 异常日志分页查询：page 0 起；操作人与 IP 精确、异常消息模糊；时间为创建时间范围（闭区间，ISO 形态）。
 */
public record ExceptionLogPageQuery(
        Integer page,
        Integer size,
        String username,
        String ip,
        String message,
        String startTime,
        String endTime) {

    /** 起始创建时间解析，格式非法按 400 业务拒绝 */
    public LocalDateTime startAt() {
        return parse(startTime);
    }

    /** 截止创建时间解析，格式非法按 400 业务拒绝 */
    public LocalDateTime endAt() {
        return parse(endTime);
    }

    private static LocalDateTime parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.bad");
        }
    }
}
