package com.agentplatform.framework.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * JWT 注销黑名单服务。
 *
 * <p>位于技术框架的安全层，维护「已注销 Token 的 jti 集合」（存 Redis）。
 * 键的 TTL 精确等于 Token 剩余有效期，Token 自然过期后黑名单条目也自动清理，避免无限增长。</p>
 */
@Service
public class TokenBlacklistService {
    /** Redis 键前缀，后接 jti */
    private static final String PREFIX = "auth:blacklist:";
    private final StringRedisTemplate redis;

    /**
     * @param redis Redis 字符串模板
     */
    public TokenBlacklistService(StringRedisTemplate redis) { this.redis = redis; }

    /**
     * 将 Token 加入黑名单。
     *
     * <p>只保存到原 Token 过期为止；已过期的 Token 无需入黑名单。
     * Redis 异常时静默忽略：登出接口不应因缓存故障返回失败。</p>
     *
     * @param jti       Token 唯一标识
     * @param expiresAt Token 过期时间
     */
    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) return;
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (!ttl.isNegative() && !ttl.isZero()) redis.opsForValue().set(PREFIX + jti, "1", ttl);
        } catch (RuntimeException ignored) { }
    }

    /**
     * 判断 Token 是否已被注销。
     *
     * <p>Redis 不可用时返回 {@code false}（fail-open），保证认证可用性优先。</p>
     *
     * @param jti Token 唯一标识
     * @return 在黑名单中返回 {@code true}
     */
    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        try { return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti)); }
        catch (RuntimeException e) { return false; }
    }
}
