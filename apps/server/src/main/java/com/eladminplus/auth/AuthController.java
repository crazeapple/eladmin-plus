package com.eladminplus.auth;

import com.eladminplus.auth.session.AuthSessionProperties;
import com.eladminplus.auth.session.LoginService;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import com.eladminplus.common.error.BusinessException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证端点：登录（滑块 + RSA + BCrypt）、刷新旋转、登出。
 * 刷新的双保险：SameSite cookie 收窄 path + 自定义头必备（CSRF 附加层）。
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LoginService loginService;
    private final TokenService tokenService;
    private final AuthSessionProperties props;

    public AuthController(LoginService loginService, TokenService tokenService, AuthSessionProperties props) {
        this.loginService = loginService;
        this.tokenService = tokenService;
        this.props = props;
    }

    public record LoginRequest(String username, String password, String keyId, String ticket) {}

    @PostMapping("/login")
    public ResponseEntity<R<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        var result = loginService.login(request.username(), request.password(), request.keyId(), request.ticket());
        return tokenResponse(result.tokens().accessToken(), result.tokens().expiresInSeconds(),
                result.tokens().refreshCookieValue());
    }

    @PostMapping("/refresh")
    public ResponseEntity<R<Map<String, Object>>> refresh(
            @RequestHeader(value = "X-Requested-With", required = false) String requestedWith,
            @CookieValue(value = "${auth.session.cookie-name}", required = false) String refreshCookie) {
        // CSRF 附加层：自定义头缺失即拒绝（SameSite 已挡跨站携带，此为纵深）
        if (requestedWith == null || requestedWith.isBlank()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "error.auth.csrf-required");
        }
        if (refreshCookie == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.refresh-missing");
        }
        // cookie 值 = userId.tokenId.random
        String[] parts = refreshCookie.split("\\.");
        if (parts.length != 3) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.refresh-missing");
        }
        long userId;
        try {
            userId = Long.parseLong(parts[0]);
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.refresh-missing");
        }
        TokenService.TokenPair tokens = tokenService.rotate(userId, parts[1], parts[2]);
        if (tokens == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "error.auth.refresh-invalid");
        }
        return tokenResponse(tokens.accessToken(), tokens.expiresInSeconds(), tokens.refreshCookieValue());
    }

    /** 登出：删会话与其 refresh；幂等——无会话同样 200 空载荷 */
    @PostMapping("/logout")
    public R<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            tokenService.revoke(authorization.substring(BEARER_PREFIX.length()));
        }
        return R.ok();
    }

    /** 统一响应：body 发 access，Set-Cookie 发旋转后的 refresh */
    private ResponseEntity<R<Map<String, Object>>> tokenResponse(
            String accessToken, long expiresInSeconds, String refreshCookieValue) {
        ResponseCookie cookie = ResponseCookie.from(props.cookieName(), refreshCookieValue)
                .httpOnly(true)
                .sameSite(props.cookieSameSite())
                .path(props.cookiePath())
                .maxAge(props.refreshTokenTtl())
                .secure(props.cookieSecure())
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(R.ok(Map.of("accessToken", accessToken, "expiresIn", expiresInSeconds)));
    }
}
