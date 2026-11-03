package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.mapper.MenuMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 身份域菜单种子（V4）：系统管理目录、四子菜单、18 条按钮权限点行，幂等在册。 */
@SpringBootTest
class MenuSeedTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void 系统管理目录与四子菜单在册且组件路径对齐前端视图() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu directory = menus.stream()
                .filter(menu -> menu.getType() == Menu.TYPE_DIRECTORY)
                .findFirst().orElseThrow();
        assertThat(directory.getI18nKey()).isEqualTo("routes.system");

        List<String> pathKeys = menus.stream()
                .filter(menu -> menu.getType() == Menu.TYPE_MENU && menu.getParentId() != null)
                .map(Menu::getComponentPathKey)
                .toList();
        assertThat(pathKeys).containsExactlyInAnyOrder(
                "system/users", "system/roles", "system/depts", "system/jobs");
        // 子菜单挂目录之下
        Menu users = menus.stream()
                .filter(menu -> "system/users".equals(menu.getComponentPathKey()))
                .findFirst().orElseThrow();
        assertThat(users.getParentId()).isEqualTo(directory.getId());
    }

    @Test
    void 十八条按钮权限点行与后端权限标识一致() {
        List<Menu> buttons = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getType, Menu.TYPE_BUTTON));
        assertThat(buttons).extracting(Menu::getPermission).containsExactlyInAnyOrder(
                "system:dept:list", "system:dept:add", "system:dept:edit", "system:dept:del",
                "system:job:list", "system:job:add", "system:job:edit", "system:job:del",
                "system:user:list", "system:user:add", "system:user:edit",
                "system:user:resetPwd", "system:user:del",
                "system:role:list", "system:role:add", "system:role:edit", "system:role:del",
                "system:menu:list");
        // 每条按钮挂对应页面菜单、授权树文案 key 在册
        assertThat(buttons).allSatisfy(button -> {
            assertThat(button.getParentId()).isNotNull();
            assertThat(button.getI18nKey()).startsWith("perms.");
        });
    }

    @Test
    void 迁移幂等不产生重复行() {
        // 全表行数精确：V2 三条页面 + 目录 1 + 子菜单 4 + 按钮 18 = 26
        assertThat(menuMapper.selectCount(null)).isEqualTo(26);
        // 权限点标识唯一（NOT EXISTS 守卫语义）
        List<Menu> menus = menuMapper.selectList(null);
        long distinctPermissions = menus.stream()
                .map(Menu::getPermission)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        assertThat(distinctPermissions).isEqualTo(18);
    }
}
