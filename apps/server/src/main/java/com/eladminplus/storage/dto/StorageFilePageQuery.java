package com.eladminplus.storage.dto;

import com.eladminplus.common.error.BusinessException;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

/** 存储文件分页查询：page 0 起；原名模糊、上传人精确；时间为创建时间范围（闭区间，ISO 形态）。 */
public record StorageFilePageQuery(
        Integer page, Integer size, String name, String uploader, String startTime, String endTime) {

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
