package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.mapper.MenuMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 菜单图标种子完整性（V14 补齐）：目录与页面菜单 icon 库值全量非空且为双前缀形态
 * （svg: 本地集 / ep: Element Plus 集），按钮权限点行不涉图标保持 NULL。
 * 前端静态映射兜底表以本完整性为退役前提。
 */
@SpringBootTest
class MenuIconSeedTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void 目录与页面菜单icon库值全量非空且为双前缀形态() {
        List<Menu> menus = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().in(Menu::getType, Menu.TYPE_DIRECTORY, Menu.TYPE_MENU));
        assertThat(menus).isNotEmpty();
        assertThat(menus).allSatisfy(menu -> {
            String icon = menu.getIcon();
            assertThat(icon).as("菜单 %s icon 库值", menu.getI18nKey()).isNotNull();
            assert icon != null;
            assertThat(icon.startsWith("svg:") || icon.startsWith("ep:"))
                    .as("菜单 %s icon 库值前缀", menu.getI18nKey())
                    .isTrue();
        });
    }

    @Test
    void 按钮权限点行不涉图标库值保持NULL() {
        List<Menu> buttons = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getType, Menu.TYPE_BUTTON));
        assertThat(buttons).isNotEmpty();
        assertThat(buttons).allSatisfy(button -> assertThat(button.getIcon()).isNull());
    }

    @Test
    void 存量八菜单补值入库且语义择优落定() {
        List<Menu> menus = menuMapper.selectList(null);
        assertThat(iconOf(menus, "routes.home")).isEqualTo("svg:index");
        assertThat(iconOf(menus, "routes.demoCached")).isEqualTo("svg:tab");
        assertThat(iconOf(menus, "routes.demoNoCache")).isEqualTo("ep:refresh-left");
        assertThat(iconOf(menus, "routes.system")).isEqualTo("svg:system");
        assertThat(iconOf(menus, "routes.systemUsers")).isEqualTo("svg:user");
        assertThat(iconOf(menus, "routes.systemRoles")).isEqualTo("svg:role");
        assertThat(iconOf(menus, "routes.systemDepts")).isEqualTo("svg:dept");
        assertThat(iconOf(menus, "routes.systemJobs")).isEqualTo("svg:people");
    }

    private String iconOf(List<Menu> menus, String i18nKey) {
        return menus.stream()
                .filter(menu -> i18nKey.equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() != Menu.TYPE_BUTTON)
                .map(Menu::getIcon)
                .findFirst().orElseThrow();
    }
}
