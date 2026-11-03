package com.eladminplus.auth.session;

import com.eladminplus.auth.domain.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 不透明令牌服务：accessToken 即会话键（Redis hash，删键即强退）；
 * refreshToken 按 userId+tokenId 落 Redis，旋转 = 旧键 TTL 收窄到宽限窗口 + 新键全长下发。
 */
@Service
public class TokenService {

    public static final String TOKEN_PREFIX = "auth:token:";
    public static final String REFRESH_PREFIX = "auth:refresh:";
    /** per-user 会话索引：停用强退按 userId 精确删全部 access 会话 */
    public static final String SESSION_INDEX_PREFIX = "auth:user-sessions:";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final AuthSessionProperties props;
    private final AuthContextService authContextService;

    public TokenService(StringRedisTemplate redis, AuthSessionProperties props, AuthContextService authContextService) {
        this.redis = redis;
        this.props = props;
        this.authContextService = authContextService;
    }

    /** 一次登录的令牌对与上下文 */
    public record TokenPair(String accessToken, long expiresInSeconds, String refreshCookieValue, long refreshMaxAgeSeconds) {}

    /** 会话上下文：过滤链按 accessToken 还原身份 */
    public record SessionContext(
            Long userId, String username, String tokenId, List<String> roles, List<String> permissions, Instant loginAt) {}

    /** 签发令牌对：会话与 refresh 同时落 Redis */
    public TokenPair issue(User user, List<String> roles, List<String> permissions) {
        String accessToken = randomToken();
        String tokenId = UUID.randomUUID().toString();

        Map<String, String> session = new HashMap<>();
        session.put("userId", String.valueOf(user.getId()));
        session.put("username", user.getUsername());
        session.put("tokenId", tokenId);
        session.put("roles", String.join(",", roles));
        session.put("permissions", String.join(",", permissions));
        session.put("loginAt", Instant.now().toString());
        redis.opsForHash().putAll(TOKEN_PREFIX + accessToken, session);
        redis.expire(TOKEN_PREFIX + accessToken, props.accessTokenTtl().getSeconds(), TimeUnit.SECONDS);
        redis.opsForSet()
                .add(SESSION_INDEX_PREFIX + user.getId(), accessToken);
        redis.expire(SESSION_INDEX_PREFIX + user.getId(), props.accessTokenTtl().getSeconds(), TimeUnit.SECONDS);

        String refreshRandom = randomToken();
        redis.opsForValue()
                .set(refreshKey(user.getId(), tokenId), refreshRandom, props.refreshTokenTtl().getSeconds(),
                        TimeUnit.SECONDS);
        String cookieValue = user.getId() + "." + tokenId + "." + refreshRandom;
        return new TokenPair(
                accessToken, props.accessTokenTtl().getSeconds(), cookieValue, props.refreshTokenTtl().getSeconds());
    }

    /** accessToken → 会话上下文；不存在（过期 / 强退 / 登出）返回 null */
    public SessionContext resolve(String accessToken) {
        Map<Object, Object> hash = redis.opsForHash().entries(TOKEN_PREFIX + accessToken);
        if (hash.isEmpty()) {
            return null;
        }
        return new SessionContext(
                Long.valueOf((String) hash.get("userId")),
                (String) hash.get("username"),
                (String) hash.get("tokenId"),
                splitJoined((String) hash.get("roles")),
                splitJoined((String) hash.get("permissions")),
                Instant.parse((String) hash.get("loginAt")));
    }

    /**
     * refresh 旋转：值匹配即签发全新令牌对；旧键不删、TTL 收窄到宽限窗口——
     * 多 tab 并发在窗口内各自旋转各得新对（前端 Web Locks 单飞失效时的后端保险）。
     * 键不存在或值不匹配（过期 / 伪造）返回 null。
     */
    public TokenPair rotate(Long userId, String tokenId, String random) {
        String key = refreshKey(userId, tokenId);
        String stored = redis.opsForValue().get(key);
        if (stored == null || !stored.equals(random)) {
            return null;
        }
        var context = authContextService.load(userId);
        if (context.user() == null) {
            return null;
        }
        // 宽限窗口：旧键短命留存（从本次使用起计）
        redis.opsForValue().set(key, stored, props.graceWindow().getSeconds(), TimeUnit.SECONDS);
        return issue(context.user(), context.roles(), context.permissions());
    }

    /** 登出：删会话与其 refresh 键,并从用户会话索引移除 */
    public void revoke(String accessToken) {
        SessionContext context = resolve(accessToken);
        if (context == null) {
            return;
        }
        redis.delete(TOKEN_PREFIX + accessToken);
        redis.delete(refreshKey(context.userId(), context.tokenId()));
        redis.opsForSet().remove(SESSION_INDEX_PREFIX + context.userId(), accessToken);
    }

    /**
     * 按用户强退：删该用户全部 access 会话与 refresh 键并清索引。
     * refresh 键含 userId 路径段,SCAN 精确前缀遍历;停用账号场景调用(量小可靠)。
     */
    public void revokeAllForUser(Long userId) {
        var members = redis.opsForSet().members(SESSION_INDEX_PREFIX + userId);
        if (members != null) {
            members.forEach(token -> redis.delete(TOKEN_PREFIX + token));
        }
        redis.delete(SESSION_INDEX_PREFIX + userId);
        java.util.Set<String> refreshKeys = redis.keys(REFRESH_PREFIX + userId + ":*");
        if (refreshKeys != null && !refreshKeys.isEmpty()) {
            redis.delete(refreshKeys);
        }
    }

    public static String refreshKey(Long userId, String tokenId) {
        return REFRESH_PREFIX + userId + ":" + tokenId;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static List<String> splitJoined(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return List.of(joined.split(","));
    }
}
