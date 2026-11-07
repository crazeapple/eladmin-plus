package com.eladminplus.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V17 菜单图标源替换：svg: 库值语义映射为 nv: ionicons 名。上下文启动即执行 Flyway，
 * 断言落库结果——无 svg: 前缀残留、在用 21 值逐一落在对应 nv 名、ep: 值不动。
 */
@SpringBootTest
class MenuIconMigrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    /** 迁移映射面（与 V17 CASE 一致）：种子菜单 i18n_key → 库值应落的 nv 名 */
    private static final Map<String, String> SEED_NV_ICONS = Map.ofEntries(
            Map.entry("routes.home", "nv:home-outline"),
            Map.entry("routes.systemUsers", "nv:person-outline"),
            Map.entry("routes.systemRoles", "nv:person-circle-outline"),
            Map.entry("routes.systemDepts", "nv:business-outline"),
            Map.entry("routes.systemJobs", "nv:people-outline"),
            Map.entry("routes.monitorOnlineUsers", "nv:people-circle-outline"),
            Map.entry("routes.systemMenus", "nv:menu-outline"),
            Map.entry("routes.systemDicts", "nv:library-outline"),
            Map.entry("routes.systemTasks", "nv:time-outline"),
            Map.entry("routes.monitor", "nv:speedometer-outline"),
            Map.entry("routes.monitorOperationLogs", "nv:document-text-outline"),
            Map.entry("routes.monitorExceptionLogs", "nv:warning-outline"),
            Map.entry("routes.monitorServerInfo", "nv:server-outline"),
            Map.entry("routes.system", "nv:cog-outline"),
            Map.entry("routes.tools", "nv:construct-outline"),
            Map.entry("routes.database", "nv:albums-outline"),
            Map.entry("routes.ops", "nv:rocket-outline"),
            Map.entry("routes.toolsEmail", "nv:mail-outline"),
            Map.entry("routes.toolsStorage", "nv:cloud-outline"),
            Map.entry("routes.toolsAliPay", "nv:wallet-outline"),
            Map.entry("routes.demo", "nv:code-slash-outline"));

    @Test
    void 迁移后无svg前缀残留() {
        Integer svgCount =
                jdbc.queryForObject("SELECT COUNT(*) FROM menu WHERE icon LIKE 'svg:%'", Integer.class);
        assertThat(svgCount).isZero();
    }

    @Test
    void 在用21值逐一映射为对应nv名() {
        for (Map.Entry<String, String> seed : SEED_NV_ICONS.entrySet()) {
            List<String> icons =
                    jdbc.queryForList(
                            "SELECT icon FROM menu WHERE i18n_key = ? AND icon IS NOT NULL",
                            String.class,
                            seed.getKey());
            assertThat(icons).as("%s 库值", seed.getKey()).containsExactly(seed.getValue());
        }
    }

    @Test
    void ep库值不受迁移影响() {
        String demoCached =
                jdbc.queryForObject(
                        "SELECT icon FROM menu WHERE i18n_key = 'routes.demoCached' AND type = 1",
                        String.class);
        assertThat(demoCached).isEqualTo("ep:box");
        String demoNoCache =
                jdbc.queryForObject(
                        "SELECT icon FROM menu WHERE i18n_key = 'routes.demoNoCache' AND type = 1",
                        String.class);
        assertThat(demoNoCache).isEqualTo("ep:refresh-left");
    }
}
