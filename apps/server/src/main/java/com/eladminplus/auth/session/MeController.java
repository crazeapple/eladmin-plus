package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.MenuMapper;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.R;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户区信息：用户 + 角色码 + 权限点 + 菜单树（前端动态路由与页签缓存的唯一真相）。
 * 菜单按角色关联组装；ROLE_ROOT 角色位直通全菜单（与权限判定同语义）。
 */
@RestController
@RequestMapping("/auth/me")
public class MeController {

    /** 菜单树节点：字段与 menu 表对齐，children 升序排列 */
    public record MenuNode(
            Long id,
            Long parentId,
            Integer type,
            String permission,
            String i18nKey,
            String componentPathKey,
            Boolean noCache,
            Integer sort,
            List<MenuNode> children) {}

    public record MeResponse(UserView user, List<String> roles, List<String> permissions, List<MenuNode> menus) {}

    public record UserView(Long id, String username, String nickName) {}

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public MeController(
            UserMapper userMapper,
            RoleMapper roleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    @GetMapping
    public R<MeResponse> me(Authentication authentication) {
        // 过滤链注入的 details 即会话上下文
        TokenService.SessionContext context = (TokenService.SessionContext) authentication.getDetails();
        User user = userMapper.selectById(context.userId());

        List<Menu> menus = context.roles().contains("ROLE_ROOT")
                ? allMenus()
                : menusOfRoles(context.roles());

        return R.ok(new MeResponse(
                new UserView(user.getId(), user.getUsername(), user.getNickName()),
                context.roles(),
                context.permissions(),
                asTree(menus)));
    }

    private List<Menu> allMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSort));
    }

    private List<Menu> menusOfRoles(List<String> roleCodes) {
        if (roleCodes.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = roleMapper
                .selectList(new LambdaQueryWrapper<Role>().in(Role::getCode, roleCodes))
                .stream()
                .map(Role::getId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper
                .selectList(new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, roleIds))
                .stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().in(Menu::getId, menuIds).orderByAsc(Menu::getSort));
    }

    /** 平铺菜单组装为树（parentId 关联，同层按 sort 升序） */
    private List<MenuNode> asTree(List<Menu> menus) {
        List<MenuNode> nodes = menus.stream()
                .map(menu -> new MenuNode(
                        menu.getId(),
                        menu.getParentId(),
                        menu.getType(),
                        menu.getPermission(),
                        menu.getI18nKey(),
                        menu.getComponentPathKey(),
                        menu.getNoCache(),
                        menu.getSort(),
                        new ArrayList<>()))
                .toList();
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes) {
            if (node.parentId() == null) {
                roots.add(node);
            } else {
                nodes.stream()
                        .filter(parent -> parent.id().equals(node.parentId()))
                        .findFirst()
                        .ifPresent(parent -> parent.children().add(node));
            }
        }
        Comparator<MenuNode> bySort = Comparator.comparing(MenuNode::sort);
        roots.sort(bySort);
        roots.forEach(root -> root.children().sort(bySort));
        return roots;
    }
}
