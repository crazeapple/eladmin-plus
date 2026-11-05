package com.eladminplus.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V9 存储域迁移：storage_file 表与列在册、唯一约束与筛选索引在册、user 表头像列在册。上下文启动即执行 Flyway，断言落库结果。 */
@SpringBootTest
class StorageSchemaTest {

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
    void 存储文件表与列在册() {
        assertThat(columns("storage_file"))
                .contains("name", "storage_key", "ext", "size", "mime", "engine", "source", "uploader", "created_at");
    }

    @Test
    void 存储键唯一约束与筛选索引在册() {
        assertThat(indexes("storage_file")).contains("uk_storage_file_key", "idx_storage_file_created_at",
                "idx_storage_file_uploader");
    }

    @Test
    void 用户表头像列在册() {
        assertThat(columns("user")).contains("avatar");
    }
}
