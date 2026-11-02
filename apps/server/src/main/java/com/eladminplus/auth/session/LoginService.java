package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.captcha.BlockPuzzleService;
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
import com.eladminplus.auth.rsa.RsaKeyService;
import com.eladminplus.common.error.BusinessException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录全链：票据即焚 → RSA 解密（keyId 定位）→ BCrypt 比对 → 签发令牌对。
 * 失败统一「账号或密码错误」，不区分账号不存在与密码不符（防账号枚举）。
 */
@Service
public class LoginService {

    /** 登录结果：access 载荷 + refresh cookie 值（cookie 属性由控制器统一拼装） */
    public record LoginResult(TokenService.TokenPair tokens, String refreshCookieValue) {}

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMenuMapper roleMenuMapper;
    private final MenuMapper menuMapper;
    private final RsaKeyService rsaKeyService;
    private final BlockPuzzleService blockPuzzleService;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public LoginService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            RoleMenuMapper roleMenuMapper,
            MenuMapper menuMapper,
            RsaKeyService rsaKeyService,
            BlockPuzzleService blockPuzzleService,
            TokenService tokenService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMenuMapper = roleMenuMapper;
        this.menuMapper = menuMapper;
        this.rsaKeyService = rsaKeyService;
        this.blockPuzzleService = blockPuzzleService;
        this.tokenService = tokenService;
    }

    public LoginResult login(String username, String cipherText, String keyId, String ticket) {
        // 票据一次性：消费失败即无效或重放
        if (!blockPuzzleService.consumeTicket(ticket)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.captcha-invalid");
        }
        // RSA 解密：keyId 格式 / 公钥过期 / 密文损坏分语义（均 400）
        String plainPassword = rsaKeyService.decrypt(cipherText, keyId);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null || !passwordEncoder.matches(plainPassword, user.getPassword())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.login-failed");
        }
        TokenService.TokenPair tokens = tokenService.issue(user, rolesOf(user.getId()), permissionsOf(user.getId()));
        return new LoginResult(tokens, tokens.refreshCookieValue());
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
