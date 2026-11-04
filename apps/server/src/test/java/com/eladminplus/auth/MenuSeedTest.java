package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.mapper.MenuMapper;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 菜单种子（V4 身份域 + V6 配置域 + V8 监控域）：两目录、十一子菜单、34 条按钮权限点行，幂等在册。 */
@SpringBootTest
class MenuSeedTest {

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void 系统管理目录与七子菜单在册且组件路径对齐前端视图() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu directory = menus.stream()
                .filter(menu -> "routes.system".equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() == Menu.TYPE_DIRECTORY)
                .findFirst().orElseThrow();
        assertThat(directory.getI18nKey()).isEqualTo("routes.system");

        List<String> pathKeys = menus.stream()
                .filter(menu -> menu.getType() == Menu.TYPE_MENU)
                .filter(menu -> directory.getId().equals(menu.getParentId()))
                .map(Menu::getComponentPathKey)
                .toList();
        assertThat(pathKeys).containsExactlyInAnyOrder(
                "system/users", "system/roles", "system/depts", "system/jobs",
                "system/menus", "system/dicts", "system/tasks");
        // 子菜单挂目录之下
        Menu users = menus.stream()
                .filter(menu -> "system/users".equals(menu.getComponentPathKey()))
                .findFirst().orElseThrow();
        assertThat(users.getParentId()).isEqualTo(directory.getId());
    }

    @Test
    void 配置域三子菜单图标库值入库且排序殿后() {
        List<Menu> menus = menuMapper.selectList(null);
        assertThat(menus)
                .filteredOn(menu -> "system/menus".equals(menu.getComponentPathKey()))
                .first()
                .satisfies(menu -> {
                    assertThat(menu.getI18nKey()).isEqualTo("routes.systemMenus");
                    assertThat(menu.getIcon()).isEqualTo("svg:menu");
                    assertThat(menu.getSort()).isEqualTo(5);
                });
        assertThat(menus)
                .filteredOn(menu -> "system/dicts".equals(menu.getComponentPathKey()))
                .first()
                .satisfies(menu -> {
                    assertThat(menu.getI18nKey()).isEqualTo("routes.systemDicts");
                    assertThat(menu.getIcon()).isEqualTo("svg:dictionary");
                    assertThat(menu.getSort()).isEqualTo(6);
                });
        assertThat(menus)
                .filteredOn(menu -> "system/tasks".equals(menu.getComponentPathKey()))
                .first()
                .satisfies(menu -> {
                    assertThat(menu.getI18nKey()).isEqualTo("routes.systemTasks");
                    assertThat(menu.getIcon()).isEqualTo("svg:timing");
                    assertThat(menu.getSort()).isEqualTo(7);
                });
    }

    @Test
    void 监控域目录与四子菜单在册且组件路径对齐前端视图() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu directory = menus.stream()
                .filter(menu -> "routes.monitor".equals(menu.getI18nKey()))
                .filter(menu -> menu.getType() == Menu.TYPE_DIRECTORY)
                .findFirst().orElseThrow();
        assertThat(directory.getIcon()).isEqualTo("svg:monitor");
        assertThat(directory.getSort()).isEqualTo(5);

        // 四子菜单挂监控目录之下：组件路径对齐前端视图、icon 库值入库、sort 1~4
        assertThat(menus)
                .filteredOn(menu -> menu.getType() == Menu.TYPE_MENU
                        && directory.getId().equals(menu.getParentId()))
                .extracting(Menu::getComponentPathKey, Menu::getI18nKey,
                        Menu::getIcon, Menu::getSort)
                .containsExactlyInAnyOrder(
                        tuple("monitor/operation-logs", "routes.monitorOperationLogs", "svg:log", 1),
                        tuple("monitor/exception-logs", "routes.monitorExceptionLogs", "svg:error", 2),
                        tuple("monitor/online-users", "routes.monitorOnlineUsers", "svg:peoples", 3),
                        tuple("monitor/server-info", "routes.monitorServerInfo", "svg:server", 4));
    }

    @Test
    void 三十四条按钮权限点行与后端权限标识一致() {
        List<Menu> buttons = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getType, Menu.TYPE_BUTTON));
        assertThat(buttons).extracting(Menu::getPermission).containsExactlyInAnyOrder(
                "system:dept:list", "system:dept:add", "system:dept:edit", "system:dept:del",
                "system:job:list", "system:job:add", "system:job:edit", "system:job:del",
                "system:user:list", "system:user:add", "system:user:edit",
                "system:user:resetPwd", "system:user:del",
                "system:role:list", "system:role:add", "system:role:edit", "system:role:del",
                "system:menu:list", "system:menu:add", "system:menu:edit", "system:menu:del",
                "system:dict:list", "system:dict:add", "system:dict:edit", "system:dict:del",
                "system:task:list", "system:task:add", "system:task:edit", "system:task:del",
                "monitor:log:list", "monitor:log:del",
                "monitor:online:list", "monitor:online:del",
                "monitor:monitor:list");
        // 每条按钮挂对应页面菜单、授权树文案 key 在册
        assertThat(buttons).allSatisfy(button -> {
            assertThat(button.getParentId()).isNotNull();
            assertThat(button.getI18nKey()).startsWith("perms.");
        });
    }

    @Test
    void 监控域五个按钮权限点挂对应页面菜单且异常日志页不重复挂() {
        List<Menu> menus = menuMapper.selectList(null);
        // 操作日志页挂 log 组两点（异常日志页共用 log 组，不重复挂）
        assertThat(buttonPermissionsOf(menus, "monitor/operation-logs")).containsExactlyInAnyOrder(
                "monitor:log:list", "monitor:log:del");
        assertThat(buttonPermissionsOf(menus, "monitor/exception-logs")).isEmpty();
        assertThat(buttonPermissionsOf(menus, "monitor/online-users")).containsExactlyInAnyOrder(
                "monitor:online:list", "monitor:online:del");
        assertThat(buttonPermissionsOf(menus, "monitor/server-info")).containsExactlyInAnyOrder(
                "monitor:monitor:list");
    }

    @Test
    void 配置域十二个按钮权限点挂对应页面菜单() {
        List<Menu> menus = menuMapper.selectList(null);
        assertThat(buttonPermissionsOf(menus, "system/menus")).containsExactlyInAnyOrder(
                "system:menu:list", "system:menu:add", "system:menu:edit", "system:menu:del");
        assertThat(buttonPermissionsOf(menus, "system/dicts")).containsExactlyInAnyOrder(
                "system:dict:list", "system:dict:add", "system:dict:edit", "system:dict:del");
        assertThat(buttonPermissionsOf(menus, "system/tasks")).containsExactlyInAnyOrder(
                "system:task:list", "system:task:add", "system:task:edit", "system:task:del");
    }

    @Test
    void 共用菜单树查询点改挂菜单页首行() {
        List<Menu> menus = menuMapper.selectList(null);
        Menu menusPage = menus.stream()
                .filter(menu -> "system/menus".equals(menu.getComponentPathKey()))
                .findFirst().orElseThrow();
        Menu sharedList = menus.stream()
                .filter(menu -> "system:menu:list".equals(menu.getPermission()))
                .findFirst().orElseThrow();
        assertThat(sharedList.getParentId()).isEqualTo(menusPage.getId());
        assertThat(sharedList.getSort()).isEqualTo(1);
    }

    @Test
    void 迁移幂等不产生重复行() {
        // 全表行数精确：V2 三条页面 + 目录 2 + 子菜单 11 + 按钮 34 = 50
        assertThat(menuMapper.selectCount(null)).isEqualTo(50);
        // 权限点标识唯一（NOT EXISTS 守卫语义）
        List<Menu> menus = menuMapper.selectList(null);
        long distinctPermissions = menus.stream()
                .map(Menu::getPermission)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        assertThat(distinctPermissions).isEqualTo(34);
    }

    private List<String> buttonPermissionsOf(List<Menu> menus, String componentPathKey) {
        Menu page = menus.stream()
                .filter(menu -> componentPathKey.equals(menu.getComponentPathKey()))
                .findFirst().orElseThrow();
        return menus.stream()
                .filter(menu -> menu.getType() == Menu.TYPE_BUTTON)
                .filter(menu -> page.getId().equals(menu.getParentId()))
                .map(Menu::getPermission)
                .toList();
    }
}
