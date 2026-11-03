package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.mapper.MenuMapper;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 菜单树构建（/auth/me 与角色授权共用）：全量树、按角色过滤集、平铺转树（parentId 关联、同层 sort 升序）。
 */
@Service
public class MenuTreeService {

    /** 菜单树节点：字段与 menu 表对齐，children 升序排列 */
    public record MenuNode(
            Long id,
            Long parentId,
            Integer type,
            String permission,
            String i18nKey,
            String componentPathKey,
            String icon,
            Boolean hidden,
            Boolean noCache,
            Integer sort,
            List<MenuNode> children) {}

    private final MenuMapper menuMapper;
    private final RoleMapper roleMapper;
    private final RoleMenuMapper roleMenuMapper;

    public MenuTreeService(MenuMapper menuMapper, RoleMapper roleMapper, RoleMenuMapper roleMenuMapper) {
        this.menuMapper = menuMapper;
        this.roleMapper = roleMapper;
        this.roleMenuMapper = roleMenuMapper;
    }

    /** 全量菜单树（角色授权数据源） */
    public List<MenuNode> fullTree() {
        return asTree(allMenus());
    }

    /** 用户的可见菜单集（平铺）：ROOT 直通全菜单,其余按角色关联 */
    public List<Menu> menusOfUser(TokenService.SessionContext context) {
        if (context.roles().contains("ROLE_ROOT")) {
            return allMenus();
        }
        return menusOfRoles(context.roles());
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

    /** 角色已授权的菜单 id 集合（编辑表单回显） */
    public List<Long> menuIdsOfRole(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId))
                .stream()
                .map(RoleMenu::getMenuId)
                .distinct()
                .toList();
    }

    /** 保存角色授权菜单集合（全量替换） */
    public void saveRoleMenus(Long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<RoleMenu>().eq(RoleMenu::getRoleId, roleId));
        for (Long menuId : menuIds.stream().distinct().toList()) {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }

    private List<Menu> allMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSort));
    }

    /** 平铺菜单组装为树（parentId 关联，同层按 sort 升序） */
    public List<MenuNode> asTree(List<Menu> menus) {
        List<MenuNode> nodes = menus.stream()
                .map(menu -> new MenuNode(
                        menu.getId(),
                        menu.getParentId(),
                        menu.getType(),
                        menu.getPermission(),
                        menu.getI18nKey(),
                        menu.getComponentPathKey(),
                        menu.getIcon(),
                        menu.getHidden(),
                        menu.getNoCache(),
                        menu.getSort(),
                        new java.util.ArrayList<>()))
                .toList();
        java.util.Map<Long, List<MenuNode>> byParent = new java.util.HashMap<>();
        for (MenuNode node : nodes) {
            byParent.computeIfAbsent(node.parentId(), k -> new java.util.ArrayList<>()).add(node);
        }
        for (List<MenuNode> siblings : byParent.values()) {
            siblings.sort(java.util.Comparator.comparingInt(MenuNode::sort));
        }
        List<MenuNode> roots = byParent.getOrDefault(null, List.of());
        attachChildren(roots, byParent);
        return roots;
    }

    private void attachChildren(List<MenuNode> nodes, java.util.Map<Long, List<MenuNode>> byParent) {
        for (MenuNode node : nodes) {
            List<MenuNode> children = byParent.getOrDefault(node.id(), List.of());
            node.children().addAll(children);
            attachChildren(children, byParent);
        }
    }
}
