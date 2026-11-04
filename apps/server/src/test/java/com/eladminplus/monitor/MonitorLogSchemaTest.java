package com.eladminplus.monitor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V7 监控域迁移：日志双表与列在册、轮转索引在册、清理任务预注册行在册。上下文启动即执行 Flyway，断言落库结果。 */
@SpringBootTest
class MonitorLogSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class,
                table);
    }

    private List<String> indexes(String table) {
        return jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                String.class,
                table);
    }

    @Test
    void 日志双表与列在册() {
        assertThat(columns("operation_log"))
                .contains("username", "ip", "description", "elapsed_ms", "request_param");
        assertThat(columns("exception_log"))
                .contains("username", "ip", "name", "message", "stack_trace");
    }

    @Test
    void 轮转与筛选索引在册() {
        assertThat(indexes("operation_log"))
                .contains("idx_operation_log_created_at", "idx_operation_log_username");
        assertThat(indexes("exception_log"))
                .contains("idx_exception_log_created_at", "idx_exception_log_username");
    }

    @Test
    void 清理任务预注册行在册() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT bean_name, method_name, cron, status FROM task WHERE name = '日志清理'");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("bean_name")).isEqualTo("logCleanupJob");
        assertThat(row.get("method_name")).isEqualTo("clean");
        assertThat(row.get("cron")).isEqualTo("0 0 3 * * ?");
        assertThat(((Number) row.get("status")).intValue()).isEqualTo(1);
    }
}
