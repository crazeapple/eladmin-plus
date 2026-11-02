package com.eladminplus.config;

import com.eladminplus.auth.session.TokenService;
import com.eladminplus.common.R;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;

    public SecurityConfig(MessageSource messageSource, ObjectMapper objectMapper) {
        this.messageSource = messageSource;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TokenService tokenService) throws Exception {
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
                        .authenticationEntryPoint(
                                (request, response, exception) -> writeError(response, HttpStatus.UNAUTHORIZED,
                                        "error.auth.unauthorized"))
                        .accessDeniedHandler(
                                (request, response, exception) -> writeError(response, HttpStatus.FORBIDDEN,
                                        "error.auth.forbidden")))
                .addFilterBefore(new BearerTokenFilter(tokenService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 401 / 403 统一同形结构输出（消息经 MessageSource 本地化） */
    private void writeError(HttpServletResponse response, HttpStatus status, String messageKey)
            throws java.io.IOException {
        String message = messageSource.getMessage(
                messageKey, null, messageKey, org.springframework.context.i18n.LocaleContextHolder.getLocale());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(R.error(status, message)));
    }
}
