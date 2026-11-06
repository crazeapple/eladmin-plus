package com.eladminplus.auth.preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.error.BusinessException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V13 用户级偏好：迁移在册（preferences JSON 列可空无缺省、存量行 NULL 不回填）、
 * 读路径缺省回退（NULL / 空 / 损坏 / 字段非法）、写路径全量幂等落列与非法 400 逐字段点名。
 */
@SpringBootTest
class PreferencesServiceTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private PreferencesService preferencesService;

    private Long adminId;
    private String adminPreferencesOriginal;

    @BeforeEach
    void snapshotAdmin() {
        User admin = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, "admin"));
        adminId = admin.getId();
        adminPreferencesOriginal = admin.getPreferences();
    }

    @AfterEach
    void restoreAdmin() {
        // admin 行偏好还原（写读测试落的变化不外溢到他测；还原值取进场快照，测试内的对象改写不波及）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, adminId)
                .set(User::getPreferences, adminPreferencesOriginal));
    }

    @Test
    void 迁移在册_preferences列JSON可空且无缺省() {
        List<Map<String, Object>> columns = jdbc.queryForList(
                "SELECT DATA_TYPE, IS_NULLABLE, COLUMN_DEFAULT FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user' AND COLUMN_NAME = 'preferences'");
        assertThat(columns).hasSize(1);
        Map<String, Object> column = columns.get(0);
        assertThat(column.get("DATA_TYPE")).isEqualTo("json");
        assertThat(column.get("IS_NULLABLE")).isEqualTo("YES");
        assertThat(column.get("COLUMN_DEFAULT")).isNull();
    }

    @Test
    void 存量行值为NULL不回填() {
        User admin = userMapper.selectById(adminId);
        assertThat(admin.getPreferences()).isNull();
    }

    @Test
    void 读路径_NULL与空与损坏一律缺省() {
        // NULL（迁移后存量值）即缺省
        assertThat(preferencesService.of(userMapper.selectById(adminId)))
                .isEqualTo(UserPreferences.defaults());

        // 空串、损坏 JSON、null 字面量同缺省，不抛不 500
        User admin = userMapper.selectById(adminId);
        for (String corrupt : new String[] {"", "  ", "{oops not json", "null"}) {
            admin.setPreferences(corrupt);
            assertThat(preferencesService.of(admin))
                    .as("损坏原文「%s」应回缺省", corrupt)
                    .isEqualTo(UserPreferences.defaults());
        }
    }

    @Test
    void 读路径_字段非法逐字段落缺省合法字段保留() {
        User admin = userMapper.selectById(adminId);
        admin.setPreferences(
                "{\"theme\":\"blue\",\"primaryColor\":\"not-a-color\",\"locale\":\"en\",\"iconSet\":\"ep\"}");
        UserPreferences parsed = preferencesService.of(admin);
        assertThat(parsed.theme()).isEqualTo(UserPreferences.DEFAULT_THEME);
        assertThat(parsed.primaryColor()).isEqualTo(UserPreferences.DEFAULT_PRIMARY_COLOR);
        assertThat(parsed.locale()).isEqualTo("en");
        assertThat(parsed.iconSet()).isEqualTo("ep");

        // 多余键容忍（前向兼容），合法四项原样读出
        admin.setPreferences(
                "{\"theme\":\"dark\",\"primaryColor\":\"#123abc\",\"locale\":\"zh-CN\",\"iconSet\":\"svg\",\"extra\":1}");
        parsed = preferencesService.of(admin);
        assertThat(parsed.theme()).isEqualTo("dark");
        assertThat(parsed.primaryColor()).isEqualTo("#123abc");
        assertThat(parsed.locale()).isEqualTo("zh-CN");
        assertThat(parsed.iconSet()).isEqualTo("svg");
    }

    @Test
    void 写读一致且幂等_只动preferences列() {
        PreferencesUpdateRequest request = new PreferencesUpdateRequest("dark", "#123ABC", "zh-CN", "ep");
        preferencesService.update(adminId, request);

        User written = userMapper.selectById(adminId);
        assertThat(written.getPreferences()).contains("\"theme\"").contains("\"primaryColor\"");
        assertThat(preferencesService.of(written))
                .isEqualTo(new UserPreferences("dark", "#123ABC", "zh-CN", "ep"));

        // 幂等：同值重写读回不变
        preferencesService.update(adminId, request);
        assertThat(preferencesService.of(userMapper.selectById(adminId)))
                .isEqualTo(new UserPreferences("dark", "#123ABC", "zh-CN", "ep"));

        // 资料列不受写偏好波及
        assertThat(written.getNickName()).isEqualTo(userMapper.selectById(adminId).getNickName());
        assertThat(written.getEmail()).isEqualTo(userMapper.selectById(adminId).getEmail());
    }

    @Test
    void 写非法_400逐字段点名且库值不变() {
        // 单字段非法：只点 theme，其余合法字段不误报
        assertThatThrownBy(() -> preferencesService
                .update(adminId, new PreferencesUpdateRequest("blue", "#123abc", "en", "svg")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("theme").doesNotContain("locale");
                });

        // 多字段非法：全部点名
        assertThatThrownBy(() -> preferencesService
                .update(adminId, new PreferencesUpdateRequest("x", "red", "fr", "dark")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getMessage()).contains("theme", "primaryColor", "locale", "iconSet");
                });

        // 拒写不动库值（保持进场快照原样）
        assertThat(userMapper.selectById(adminId).getPreferences()).isEqualTo(adminPreferencesOriginal);
    }
}
