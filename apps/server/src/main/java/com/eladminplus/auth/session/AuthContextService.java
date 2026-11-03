package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.domain.Menu;
import com.eladminplus.auth.domain.Role;
import com.eladminplus.auth.domain.RoleMenu;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.domain.UserRole;
import com.eladminplus.auth.mapper.MenuMapper;
import com.eladminplus.auth.mapper.RoleMapper;
import com.eladminplus.auth.mapper.RoleMenuMapper;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.mapper.UserRoleMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 用户上下文装配：登录与 refresh 旋转共用的角色 / 权限点查询。 */
@Service
public class AuthContextService {

    /** 用户 + 角色码 + 按钮型权限点 */
    public record UserContext(User user, List<String> roles, List<String> permissions) {}

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;

    public AuthContextService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
    }

    public UserContext load(Long userId) {
        User user = userMapper.selectById(userId);
        return new UserContext(user, rolesOf(userId), permissionsOf(userId));
    }

    /** 用户角色码清单（ROLE_ROOT 内置位在其中） */
    public List<String> rolesOf(Long userId) {
        List<Long> roleIds = userRoleMapper
                .selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream().map(Role::getCode).toList();
    }

    /** 按钮型权限点清单（绑菜单树，type=2） */
    /** 当前请求的会话上下文（过滤链装配的 details）；无认证态抛 401 语义 */
    public TokenService.SessionContext currentSession() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null
                && authentication.getDetails() instanceof TokenService.SessionContext context) {
            return context;
        }
        throw new com.eladminplus.common.error.BusinessException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "error.auth.unauthorized");
    }

    public List<String> permissionsOf(Long userId) {
        List<Long> roleIds = userRoleMapper
                .selectList(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, userId))
                .stream()
                .map(UserRole::getRoleId)
                .toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<Long> menuIds = roleMenuMapper
                .selectList(new LambdaQueryWrapper<RoleMenu>().in(RoleMenu::getRoleId, roleIds))
                .stream()
                .map(RoleMenu::getMenuId)
                .toList();
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return menuMapper
                .selectList(new LambdaQueryWrapper<Menu>()
                        .in(Menu::getId, menuIds)
                        .eq(Menu::getType, Menu.TYPE_BUTTON)
                        .isNotNull(Menu::getPermission))
                .stream()
                .map(Menu::getPermission)
                .distinct()
                .toList();
    }
}
