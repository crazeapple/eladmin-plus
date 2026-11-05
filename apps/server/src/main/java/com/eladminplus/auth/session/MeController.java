package com.eladminplus.auth.session;


import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.R;
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

    public record MeResponse(
            UserView user,
            List<String> roles,
            List<String> permissions,
            List<MenuTreeService.MenuNode> menus) {}

    public record UserView(
            Long id,
            String username,
            String nickName,
            Long avatarId,
            String email,
            String phone,
            Integer gender) {}

    private final UserMapper userMapper;
    private final MenuTreeService menuTreeService;

    public MeController(UserMapper userMapper, MenuTreeService menuTreeService) {
        this.userMapper = userMapper;
        this.menuTreeService = menuTreeService;
    }

    @GetMapping
    public R<MeResponse> me(Authentication authentication) {
        // 过滤链注入的 details 即会话上下文
        TokenService.SessionContext context = (TokenService.SessionContext) authentication.getDetails();
        User user = userMapper.selectById(context.userId());

        List<MenuTreeService.MenuNode> menus = menuTreeService.asTree(menuTreeService.menusOfUser(context));

        return R.ok(new MeResponse(
                new UserView(
                        user.getId(),
                        user.getUsername(),
                        user.getNickName(),
                        user.getAvatar(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getGender()),
                context.roles(),
                context.permissions(),
                menus));
    }
}
