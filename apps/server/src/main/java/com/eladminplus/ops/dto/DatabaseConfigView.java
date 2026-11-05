package com.eladminplus.ops.dto;

import com.eladminplus.ops.domain.DatabaseConfig;
import java.time.LocalDateTime;

/** 连接配置视图：密码不外发，前端编辑以留空表达「不改密码」。 */
public record DatabaseConfigView(
        Long id, String host, Integer port, String name, String userName, String remark, LocalDateTime createdAt, LocalDateTime updatedAt) {

    public static DatabaseConfigView from(DatabaseConfig row) {
        return new DatabaseConfigView(
                row.getId(),
                row.getHost(),
                row.getPort(),
                row.getName(),
                row.getUserName(),
                row.getRemark(),
                row.getCreatedAt(),
                row.getUpdatedAt());
    }
}
