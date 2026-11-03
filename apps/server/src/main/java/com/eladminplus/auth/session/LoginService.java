package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.captcha.BlockPuzzleService;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.rsa.RsaKeyService;
import com.eladminplus.common.error.BusinessException;
import org.springframework.http.HttpStatus;
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
    private final RsaKeyService rsaKeyService;
    private final BlockPuzzleService blockPuzzleService;
    private final TokenService tokenService;
    private final AuthContextService authContextService;
    private final PasswordEncoder passwordEncoder;

    public LoginService(
            UserMapper userMapper,
            RsaKeyService rsaKeyService,
            BlockPuzzleService blockPuzzleService,
            TokenService tokenService,
            AuthContextService authContextService,
            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.rsaKeyService = rsaKeyService;
        this.blockPuzzleService = blockPuzzleService;
        this.tokenService = tokenService;
        this.authContextService = authContextService;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginResult login(String username, String cipherText, String keyId, String ticket) {
        // 票据一次性：消费失败即无效或重放
        if (!blockPuzzleService.consumeTicket(ticket)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.captcha-invalid");
        }
        // RSA 解密：keyId 格式 / 公钥过期 / 密文损坏分语义（均 400）
        String plainPassword = rsaKeyService.decrypt(cipherText, keyId);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        boolean disabled = user != null && user.getEnabled() != null && user.getEnabled() == 0;
        if (user == null || disabled || !passwordEncoder.matches(plainPassword, user.getPassword())) {
            // 停用用户与密码错误同文案（不泄露账号状态,防枚举）
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.login-failed");
        }
        var context = authContextService.load(user.getId());
        TokenService.TokenPair tokens = tokenService.issue(context.user(), context.roles(), context.permissions());
        return new LoginResult(tokens, tokens.refreshCookieValue());
    }
}
