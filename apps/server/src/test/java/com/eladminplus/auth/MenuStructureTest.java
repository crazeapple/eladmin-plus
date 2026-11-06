package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.mapper.MenuMapper;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 演示菜单目录化种子（V16）：routes.demo 为 type=0 一级目录殿后（sort 取一级最大+1），
 * 两演示子菜单挂入其下（目录内 sort 1、2），按钮权限点行不涉本次迁移。
 */
@SpringBootTest
class MenuStructureTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void 演示目录在册且type为0殿后一级最大sort() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu directory = menus.stream()
                .filter(menu -> "routes.demo".equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() == Menu.TYPE_DIRECTORY)
                .findFirst().orElseThrow();
        assertThat(directory.getParentId()).isNull();
        assertThat(directory.getComponentPathKey()).isNull();
        assertThat(directory.getIcon()).isEqualTo("svg:develop");
        // 殿后：sort 等于全部一级节点最大值（无更大者）
        Integer maxTopSort = menus.stream()
                .filter(menu -> menu.getParentId() == null)
                .map(Menu::getSort)
                .max(Comparator.naturalOrder())
                .orElseThrow();
        assertThat(directory.getSort()).isEqualTo(maxTopSort);
    }

    @Test
    void 两演示子菜单挂入目录且sort一和二() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu directory = menus.stream()
                .filter(menu -> "routes.demo".equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() == Menu.TYPE_DIRECTORY)
                .findFirst().orElseThrow();
        // 页面路由与组件路径不动，keep-alive 语义字段（noCache）原值保持
        assertThat(menus)
                .filteredOn(menu -> menu.getType() == Menu.TYPE_MENU
                        && directory.getId().equals(menu.getParentId()))
                .extracting(Menu::getComponentPathKey, Menu::getI18nKey, Menu::getNoCache, Menu::getSort)
                .containsExactlyInAnyOrder(
                        tuple("demo/cached", "routes.demoCached", false, 1),
                        tuple("demo/no-cache", "routes.demoNoCache", true, 2));
    }

    @Test
    void 两子icon库值保持不回写() {
        List<Menu> menus = menuMapper.selectList(null);
        assertThat(iconOf(menus, "routes.demoCached")).isEqualTo("ep:box");
        assertThat(iconOf(menus, "routes.demoNoCache")).isEqualTo("ep:refresh-left");
    }

    @Test
    void 按钮权限点行不涉本次迁移() {
        // 种子全量 43 条按钮行原样在册，图标保持 NULL
        List<Menu> buttons = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getType, Menu.TYPE_BUTTON));
        assertThat(buttons).hasSize(43);
        assertThat(buttons).allSatisfy(button -> assertThat(button.getIcon()).isNull());
    }

    private String iconOf(List<Menu> menus, String i18nKey) {
        return menus.stream()
                .filter(menu -> i18nKey.equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() != Menu.TYPE_BUTTON)
                .map(Menu::getIcon)
                .findFirst().orElseThrow();
    }
}
