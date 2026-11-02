package com.eladminplus.auth;

import com.eladminplus.auth.session.AuthSessionProperties;
import com.eladminplus.auth.session.LoginService;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseCookie;

/** 认证端点：登录（滑块 + RSA + BCrypt）、刷新（T05 接线）、登出（T05 接线）。 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final LoginService loginService;
    private final AuthSessionProperties props;

    public AuthController(LoginService loginService, AuthSessionProperties props) {
        this.loginService = loginService;
        this.props = props;
    }

    public record LoginRequest(String username, String password, String keyId, String ticket) {}

    @PostMapping("/login")
    public ResponseEntity<R<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        var result = loginService.login(request.username(), request.password(), request.keyId(), request.ticket());
        TokenService.TokenPair tokens = result.tokens();
        ResponseCookie cookie = refreshCookie(result.refreshCookieValue());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(R.ok(Map.of(
                        "accessToken", tokens.accessToken(),
                        "expiresIn", tokens.expiresInSeconds())));
    }

    /** refresh cookie 属性：HttpOnly + SameSite + path 收窄到 /auth；Secure 由环境配置（TLS 开启即开） */
    private ResponseCookie refreshCookie(String value) {
        return ResponseCookie.from(props.cookieName(), value)
                .httpOnly(true)
                .sameSite(props.cookieSameSite())
                .path(props.cookiePath())
                .maxAge(props.refreshTokenTtl())
                .secure(props.cookieSecure())
                .build();
    }
}
