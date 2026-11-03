package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V5 配置域迁移：menu 扩列、四新表与唯一约束、QRTZ_ 官方 11 表在位。上下文启动即执行 Flyway，断言落库结果。 */
@SpringBootTest
class SystemConfigSchemaTest {

    /** Quartz JDBC JobStore 官方表组，逐一在册断言用。 */
    private static final List<String> QRTZ_TABLES = List.of(
            "QRTZ_JOB_DETAILS",
            "QRTZ_TRIGGERS",
            "QRTZ_SIMPLE_TRIGGERS",
            "QRTZ_CRON_TRIGGERS",
            "QRTZ_SIMPROP_TRIGGERS",
            "QRTZ_BLOB_TRIGGERS",
            "QRTZ_CALENDARS",
            "QRTZ_PAUSED_TRIGGER_GRPS",
            "QRTZ_FIRED_TRIGGERS",
            "QRTZ_SCHEDULER_STATE",
            "QRTZ_LOCKS");

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class,
                table);
    }

    private List<String> uniqueConstraints(String table) {
        return jdbc.queryForList(
                "SELECT DISTINCT INDEX_NAME FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND NON_UNIQUE = 0",
                String.class,
                table);
    }

    @Test
    void 菜单新列与四新表在册() {
        assertThat(columns("menu")).contains("icon", "hidden");
        assertThat(columns("dict")).contains("name", "description");
        assertThat(columns("dict_detail")).contains("dict_id", "label", "value", "sort");
        assertThat(columns("task"))
                .contains("name", "bean_name", "method_name", "param", "cron", "status");
        assertThat(columns("task_log"))
                .contains("task_id", "execute_time", "success", "duration_ms", "error_msg");
    }

    @Test
    void 唯一约束在册() {
        assertThat(uniqueConstraints("dict")).contains("uk_dict_name");
        assertThat(uniqueConstraints("dict_detail")).contains("uk_dict_value");
        assertThat(uniqueConstraints("task")).contains("uk_task_name");
    }

    @Test
    void quartz官方表组十一表逐一在册() {
        List<String> tables =
                jdbc.queryForList(
                        "SELECT TABLE_NAME FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME LIKE 'QRTZ\\_%'",
                        String.class);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(QRTZ_TABLES);
    }
}
