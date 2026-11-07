package com.eladminplus.auth.session;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eladminplus.auth.captcha.BlockPuzzleService;
import com.eladminplus.auth.domain.User;
import com.eladminplus.auth.guard.LoginGuardService;
import com.eladminplus.auth.mapper.UserMapper;
import com.eladminplus.auth.rsa.RsaKeyService;
import com.eladminplus.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录全链：失败锁定先行 → 票据即焚 → RSA 解密（keyId 定位）→ BCrypt 比对 → 签发令牌对。
 * 失败统一「账号或密码错误」，不区分账号不存在与密码不符与锁定中（防账号枚举）；
 * 认定失败即计数（未知账号同样计数，防撒网探测），成功登录清零。
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
    private final LoginGuardService loginGuardService;

    public LoginService(
            UserMapper userMapper,
            RsaKeyService rsaKeyService,
            BlockPuzzleService blockPuzzleService,
            TokenService tokenService,
            AuthContextService authContextService,
            PasswordEncoder passwordEncoder,
            LoginGuardService loginGuardService) {
        this.userMapper = userMapper;
        this.rsaKeyService = rsaKeyService;
        this.blockPuzzleService = blockPuzzleService;
        this.tokenService = tokenService;
        this.authContextService = authContextService;
        this.passwordEncoder = passwordEncoder;
        this.loginGuardService = loginGuardService;
    }

    public LoginResult login(String username, String cipherText, String keyId, String ticket, String ip, String userAgent) {
        // 失败锁定先行：锁定期内（正确密码同样）按登录失败语义拒绝，不消耗票据与解密
        if (loginGuardService.isAccountLocked(username)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.login-failed");
        }
        // 票据一次性：消费失败即无效或重放
        if (!blockPuzzleService.consumeTicket(ticket)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "error.auth.captcha-invalid");
        }
        // RSA 解密：keyId 格式 / 公钥过期 / 密文损坏分语义（均 400）
        String plainPassword = rsaKeyService.decrypt(cipherText, keyId);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        boolean disabled = user != null && user.getEnabled() != null && user.getEnabled() == 0;
        if (user == null || disabled || !passwordEncoder.matches(plainPassword, user.getPassword())) {
            // 停用用户与密码错误同文案（不泄露账号状态,防枚举）；认定失败计入锁定计数
            loginGuardService.onAuthFailure(username);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.login-failed");
        }
        loginGuardService.clearFailures(username);
        var context = authContextService.load(user.getId());
        // 随会话补落昵称、IP、UA（在线用户域消费）
        TokenService.TokenPair tokens =
                tokenService.issue(context.user(), context.roles(), context.permissions(), ip, userAgent);
        return new LoginResult(tokens, tokens.refreshCookieValue());
    }
}
