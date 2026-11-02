package com.eladminplus.auth;

import com.eladminplus.auth.session.TokenService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 过滤链测试载体：仅测试上下文注册（@Import），不进生产代码面。 */
@RestController
@RequestMapping("/auth-test-protected")
class TestProtectedController {

    /** 权限点载体：root 种子无此权限点 → 403 */
    @GetMapping("/needs-permission")
    @PreAuthorize("hasAuthority('test:any')")
    public Map<String, String> needsPermission() {
        return Map.of("ok", "yes");
    }

    /** 角色位载体：ROLE_ROOT 直通 → 200 */
    @GetMapping("/needs-root")
    @PreAuthorize("hasAuthority('ROLE_ROOT')")
    public Map<String, String> needsRoot(Authentication authentication) {
        return Map.of("principal", String.valueOf(authentication.getPrincipal()));
    }

    /** 已认证即可访问（无权限点） */
    @GetMapping("/authenticated-only")
    public Map<String, String> authenticatedOnly() {
        return Map.of("ok", "yes");
    }
}
