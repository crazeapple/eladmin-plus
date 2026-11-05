package com.eladminplus.auth.profile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.common.error.BusinessException;
import com.eladminplus.storage.domain.StorageFile;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.storage.service.StorageService;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/** 个人中心服务：资料改（邮箱格式与唯一性校验）、头像上传与清空（文件走存储域）。 */
@Service
public class ProfileService {

    /** 邮箱形态：简单分节校验（本地 @ 域名点分），细粒度合规不在后端强校验面 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private final UserMapper userMapper;
    private final StorageService storageService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    public ProfileService(
            UserMapper userMapper,
            StorageService storageService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService) {
        this.userMapper = userMapper;
        this.storageService = storageService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    /** 资料改：邮箱格式非法 422、重复 400；仅操作会话用户自己 */
    public void updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = requireUser(userId);
        if (StringUtils.hasText(request.email())) {
            if (!EMAIL_PATTERN.matcher(request.email()).matches()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "error.profile.email-invalid");
            }
            Long dupEmail = userMapper.selectCount(Wrappers.<User>lambdaQuery()
                    .eq(User::getEmail, request.email())
                    .ne(User::getId, userId));
            if (dupEmail > 0) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "error.system.email-duplicate");
            }
        }
        user.setNickName(request.nickName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setGender(request.gender());
        userMapper.updateById(user);
    }

    /** 头像上传：文件走存储域（上传人记自己），avatar 写文件 id */
    public Long uploadAvatar(Long userId, MultipartFile file) {
        User user = requireUser(userId);
        StorageFile row = storageService.upload(file, user.getUsername());
        user.setAvatar(row.getId());
        userMapper.updateById(user);
        return row.getId();
    }

    /** 头像清空：置 null 恢复无头像态（文件行不删，仅解除引用） */
    public void clearAvatar(Long userId) {
        User user = requireUser(userId);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getAvatar, null));
    }

    /** 改密：旧密码校验（错误 4xx 明确消息）→ 新密码生效 → 该用户全部会话失效（含当前，再访问 401） */
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = requireUser(userId);
        if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.profile.old-password-wrong");
        }
        User change = new User();
        change.setId(userId);
        change.setPassword(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(change);
        tokenService.revokeAllForUser(userId);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "error.system.not-found");
        }
        return user;
    }
}
