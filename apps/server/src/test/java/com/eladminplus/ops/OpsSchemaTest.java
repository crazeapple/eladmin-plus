package com.eladminplus.ops;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V11 运维域迁移：database_config 连接配置表与 backup_record 备份记录表在册、字段在册。上下文启动即执行 Flyway，断言落库结果。 */
@SpringBootTest
class OpsSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    private List<String> columns(String table) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                String.class,
                table);
    }

    @Test
    void 连接配置表与列在册() {
        assertThat(columns("database_config"))
                .contains("host", "port", "name", "user_name", "password", "remark", "created_at", "updated_at");
    }

    @Test
    void 备份记录表与列在册() {
        assertThat(columns("backup_record")).contains("file_name", "dir", "db_name", "size", "created_at");
    }
}
