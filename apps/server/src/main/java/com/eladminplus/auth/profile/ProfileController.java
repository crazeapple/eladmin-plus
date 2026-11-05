package com.eladminplus.auth.profile;

import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心端点（/auth/me 用户区）：资料改、头像上传与清空。登录即可操作自己（会话上下文取用户 id），
 * 不设权限点、不接受路径参数指定他人。
 */
@RestController
@RequestMapping("/auth/me")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody ProfileUpdateRequest request, Authentication authentication) {
        profileService.updateProfile(currentUserId(authentication), request);
        return R.ok();
    }

    @PostMapping("/avatar")
    public R<Long> uploadAvatar(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return R.ok(profileService.uploadAvatar(currentUserId(authentication), file));
    }

    @DeleteMapping("/avatar")
    public R<Void> clearAvatar(Authentication authentication) {
        profileService.clearAvatar(currentUserId(authentication));
        return R.ok();
    }

    private static Long currentUserId(Authentication authentication) {
        return ((TokenService.SessionContext) authentication.getDetails()).userId();
    }
}
