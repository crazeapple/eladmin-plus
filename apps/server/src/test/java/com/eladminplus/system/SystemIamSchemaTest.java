package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V3 身份域迁移：扩列、新表、种子在位。上下文启动即执行 Flyway，断言落库结果。 */
@SpringBootTest
class SystemIamSchemaTest {

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
    void 扩列与新表就位() {
        assertThat(columns("user"))
                .contains("email", "phone", "gender", "enabled", "dept_id");
        assertThat(columns("role")).contains("level", "data_scope");
        assertThat(columns("dept")).contains("parent_id", "name", "sort", "enabled");
        assertThat(columns("job")).contains("name", "sort", "enabled");
        assertThat(columns("user_job")).contains("user_id", "job_id");
        assertThat(columns("role_dept")).contains("role_id", "dept_id");
        assertThat(columns("translation")).contains("entity", "entity_id", "field", "locale", "value");
    }

    @Test
    void 种子部门树与岗位在册且幂等键成立() {
        Integer deptCount =
                jdbc.queryForObject("SELECT COUNT(*) FROM dept", Integer.class);
        assertThat(deptCount).isEqualTo(3);
        String rootId =
                jdbc.queryForObject(
                        "SELECT id FROM dept WHERE parent_id IS NULL AND name = '总部'", String.class);
        Integer childCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM dept WHERE parent_id = ?", Integer.class, Long.valueOf(rootId));
        assertThat(childCount).isEqualTo(2);
        Integer jobCount = jdbc.queryForObject("SELECT COUNT(*) FROM job", Integer.class);
        assertThat(jobCount).isGreaterThanOrEqualTo(1);

        Map<String, Object> admin =
                jdbc.queryForMap("SELECT dept_id FROM `user` WHERE username = 'admin'");
        assertThat(admin.get("dept_id")).isNotNull();
    }
}
