package com.eladminplus.config;

import com.eladminplus.auth.guard.IpRateLimitFilter;
import com.eladminplus.auth.guard.LoginGuardService;
import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.web.ErrorResponseWriter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * 平台过滤链：permitAll 清单之外一律 authenticated；会话无状态（令牌即会话键）。
 * 401（未登录 / 过期）与 403（无权限）分别走 entry point 与 access denied handler，
 * 响应同形 R 结构；方法级 @PreAuthorize 载体就位，权限点随业务提案逐个落。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final MessageSource messageSource;

    public SecurityConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            TokenService tokenService,
            LoginGuardService loginGuardService,
            ObjectMapper objectMapper)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 匿名清单精确到端点：/auth/me 属用户区，须过认证
                        .requestMatchers(
                                "/auth/public-key",
                                "/auth/captcha",
                                "/auth/captcha/check",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/stats/overview",
                                // 错误结构测试载体路径（仅测试构建有对应控制器，生产无路由无暴露面）
                                "/test/**",
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) -> ErrorResponseWriter.write(
                                response, HttpStatus.UNAUTHORIZED, "error.auth.unauthorized", messageSource,
                                objectMapper))
                        .accessDeniedHandler((request, response, exception) -> ErrorResponseWriter.write(
                                response, HttpStatus.FORBIDDEN, "error.auth.forbidden", messageSource,
                                objectMapper)))
                // 先注册认证过滤器定锚，限流再插其前：无凭据的刷量请求同样计窗封口
                .addFilterBefore(new BearerTokenFilter(tokenService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new IpRateLimitFilter(loginGuardService, messageSource, objectMapper),
                        BearerTokenFilter.class);
        return http.build();
    }
}
