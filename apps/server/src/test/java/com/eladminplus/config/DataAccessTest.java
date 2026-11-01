package com.eladminplus.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eladminplus.common.data.PageRequest;
import com.eladminplus.common.data.PageResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DataAccessTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 数据源连通() {
        Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(one).isEqualTo(1);
    }

    @Test
    void 迁移历史在册() {
        Long applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Long.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }

    @Test
    void 分页换算页码从零起() {
        Page<?> first = new PageRequest(0, 10).toMpPage();
        assertThat(first.getCurrent()).isEqualTo(1);
        assertThat(first.getSize()).isEqualTo(10);

        Page<String> mp = Page.of(2, 5, 42);
        mp.setRecords(List.of("a", "b"));
        PageResult<String> result = PageResult.from(mp);
        assertThat(result.content()).containsExactly("a", "b");
        assertThat(result.total()).isEqualTo(42);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
    }
}
