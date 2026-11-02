package com.eladminplus.config;

import com.eladminplus.auth.session.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bearer 令牌过滤器：Authorization → Redis 会话 → SecurityContext（authorities = 角色位 + 权限点）。 */
public class BearerTokenFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public BearerTokenFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            TokenService.SessionContext context = tokenService.resolve(header.substring(BEARER_PREFIX.length()));
            if (context != null) {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                context.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority(role)));
                context.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(context.username(), null, authorities);
                authentication.setDetails(context);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
