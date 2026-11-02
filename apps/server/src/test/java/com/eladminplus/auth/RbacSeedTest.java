package com.eladminplus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.MenuMapper;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.UserMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** RBAC 迁移与幂等种子：root 账号、ROLE_ROOT 内置角色位、初始菜单在位。 */
@SpringBootTest
class RbacSeedTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private MenuMapper menuMapper;

    @Test
    void root账号在位且密码为BCrypt哈希() {
        User admin = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, "admin"));
        assertThat(admin).isNotNull();
        assertThat(admin.getNickName()).isEqualTo("系统管理员");
        // BCrypt 哈希特征：$2 开头、60 字符
        assertThat(admin.getPassword()).startsWith("$2").hasSize(60);
    }

    @Test
    void roleRoot内置角色位在位() {
        Role root = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, "ROLE_ROOT"));
        assertThat(root).isNotNull();
        assertThat(root.getBuiltIn()).isTrue();
    }

    @Test
    void 初始菜单三条且字段齐全() {
        List<Menu> menus = menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSort));
        assertThat(menus).extracting(Menu::getComponentPathKey)
                .containsExactly("home/index", "demo/cached", "demo/no-cache");
        assertThat(menus).allSatisfy(menu -> {
            assertThat(menu.getType()).isEqualTo(Menu.TYPE_MENU);
            assertThat(menu.getI18nKey()).startsWith("routes.");
            assertThat(menu.getSort()).isPositive();
        });
        // 演示不缓存页标 noCache，页签缓存接线（P-010）按此判定
        Menu noCache = menus.stream()
                .filter(menu -> "demo/no-cache".equals(menu.getComponentPathKey()))
                .findFirst().orElseThrow();
        assertThat(noCache.getNoCache()).isTrue();
    }
}
