package com.eladminplus.auth.guard;

import com.eladminplus.common.web.ClientIp;
import com.eladminplus.common.web.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * IP 限流过滤器：登录与验证码出题 / 校验端点按滑动窗口计数（撞库与脚本刷题两个入口同窗封口），
 * 超限 429 走统一错误结构。非链内路径不过滤；对端地址解析不出（非常规容器环境）不限。
 */
public class IpRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> GUARDED_PATHS = Set.of("/auth/login", "/auth/captcha", "/auth/captcha/check");

    private final LoginGuardService guardService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    public IpRateLimitFilter(LoginGuardService guardService, MessageSource messageSource, ObjectMapper objectMapper) {
        this.guardService = guardService;
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GUARDED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = ClientIp.resolve(request);
        if (!ip.isBlank() && !guardService.isIpAllowed(ip)) {
            ErrorResponseWriter.write(
                    response, HttpStatus.TOO_MANY_REQUESTS, "error.auth.rate-limited", messageSource, objectMapper);
            return;
        }
        chain.doFilter(request, response);
    }
}
