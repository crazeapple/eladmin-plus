package com.eladminplus.auth.guard;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * 登录防护（Redis 计数，TTL 即时长，重启丢失可接受）：
 * 账号维度——连续认证失败计数，达阈值写锁定键，锁定期内正确密码同样拒绝，成功登录清零；
 * IP 维度——登录与验证码端点的滑动窗口限流（ZSET 时间线，Lua 原子裁窗 + 判限 + 记录）。
 * 键前缀沿用 auth: 语义：auth:guard:acct:{username}（失败计数）、auth:guard:acct:lock:{username}（锁定键）、
 * auth:guard:ip:{ip}（限流窗口）。
 */
@Service
public class LoginGuardService {

    public static final String ACCT_FAIL_PREFIX = "auth:guard:acct:";
    public static final String ACCT_LOCK_PREFIX = "auth:guard:acct:lock:";

    public static final String IP_PREFIX = "auth:guard:ip:";

    /** 连续失败计数：INCR 首次挂 TTL（计数窗口即锁定时长），原子免计数键无 TTL 裸奔 */
    private static final DefaultRedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1])) end "
                    + "return count",
            Long.class);

    /** IP 滑动窗口：裁窗 → 判限 → 记录 → 挂 TTL 一把原子；达上限拒绝且不记录（不因被拒请求续窗） */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', tonumber(ARGV[1])) "
                    + "if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 0 end "
                    + "redis.call('ZADD', KEYS[1], tonumber(ARGV[3]), ARGV[4]) "
                    + "redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[5])) "
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redis;
    private final AuthGuardProperties props;

    public LoginGuardService(StringRedisTemplate redis, AuthGuardProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** 锁定判定：锁定键在即锁（锁定期内正确密码同样拒绝，响应语义由调用方统一为登录失败） */
    public boolean isAccountLocked(String username) {
        return Boolean.TRUE.equals(redis.hasKey(ACCT_LOCK_PREFIX + username));
    }

    /** 认证失败计数：达阈值写锁定键并清零计数（锁定到期后不因残留计数一次失败即刻再锁） */
    public void onAuthFailure(String username) {
        String countKey = ACCT_FAIL_PREFIX + username;
        Long count = redis.execute(COUNT_SCRIPT, List.of(countKey), String.valueOf(props.acctLockTtl().toMillis()));
        if (count != null && count >= props.acctMaxFailures()) {
            redis.opsForValue()
                    .set(ACCT_LOCK_PREFIX + username, "1", props.acctLockTtl().toMillis(), TimeUnit.MILLISECONDS);
            redis.delete(countKey);
        }
    }

    /** 成功登录清零失败计数 */
    public void clearFailures(String username) {
        redis.delete(ACCT_FAIL_PREFIX + username);
    }

    /** IP 限流判定：窗口内未达上限放行并记录，达上限拒绝 */
    public boolean isIpAllowed(String ip) {
        long now = System.currentTimeMillis();
        Long allowed = redis.execute(
                RATE_LIMIT_SCRIPT,
                List.of(IP_PREFIX + ip),
                String.valueOf(now - props.ipWindow().toMillis()),
                String.valueOf(props.ipMaxRequests()),
                String.valueOf(now),
                UUID.randomUUID().toString(),
                String.valueOf(props.ipWindow().toMillis()));
        return allowed != null && allowed == 1;
    }
}
