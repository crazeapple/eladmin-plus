package com.eladminplus.auth.preferences;

import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 偏好写口（读随 me 响应带出，不设独立读端点）：全量四项幂等覆盖。
 * 登录即可操作自己（会话上下文取用户 id），不设权限点、不接受路径参数指定他人。
 */
@RestController
@RequestMapping("/profile/preferences")
public class PreferencesController {

    private final PreferencesService preferencesService;

    public PreferencesController(PreferencesService preferencesService) {
        this.preferencesService = preferencesService;
    }

    @PutMapping
    public R<Void> updatePreferences(
            @RequestBody PreferencesUpdateRequest request, Authentication authentication) {
        Long userId = ((TokenService.SessionContext) authentication.getDetails()).userId();
        preferencesService.update(userId, request);
        return R.ok();
    }
}
