package com.eladminplus.monitor.service;

import com.eladminplus.auth.session.TokenService;
import com.eladminplus.monitor.dto.OnlineUserView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线用户：登录时随会话落 Redis 的索引枚举（零查库），列表行含昵称、用户名、IP、登录时间、浏览器；
 * 强退复用 TokenService 按用户删全部会话基建（access 与 refresh 键即时失效，对含自己在内无豁免）。
 */
@Service
public class OnlineUserService {

    private final StringRedisTemplate redis;
    private final TokenService tokenService;

    public OnlineUserService(StringRedisTemplate redis, TokenService tokenService) {
        this.redis = redis;
        this.tokenService = tokenService;
    }

    /** 在线用户列表：TTL 内有效会话为准（过期会话键已删，索引残留跳过）；同用户多端多行并列 */
    public List<OnlineUserView> list() {
        List<OnlineUserView> views = new ArrayList<>();
        Set<String> indexKeys = redis.keys(TokenService.SESSION_INDEX_PREFIX + "*");
        if (indexKeys == null) {
            return views;
        }
        for (String indexKey : indexKeys) {
            Set<String> tokens = redis.opsForSet().members(indexKey);
            if (tokens == null) {
                continue;
            }
            for (String token : tokens) {
                Map<Object, Object> session = redis.opsForHash().entries(TokenService.TOKEN_PREFIX + token);
                if (session.isEmpty()) {
                    continue;
                }
                views.add(new OnlineUserView(
                        Long.valueOf((String) session.get("userId")),
                        (String) session.get("nickname"),
                        (String) session.get("username"),
                        (String) session.get("ip"),
                        (String) session.get("loginAt"),
                        (String) session.get("userAgent")));
            }
        }
        views.sort(Comparator.comparing(OnlineUserView::userId)
                .thenComparing(OnlineUserView::loginAt, Comparator.reverseOrder()));
        return views;
    }

    /** 强退：目标用户全部 access 会话与 refresh 键即时失效，再次访问 401 */
    public void kick(Long userId) {
        tokenService.revokeAllForUser(userId);
    }
}
