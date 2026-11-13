package com.agentplatform.framework.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class TokenBlacklistService {
    private static final String PREFIX = "auth:blacklist:";
    private final StringRedisTemplate redis;
    public TokenBlacklistService(StringRedisTemplate redis) { this.redis = redis; }

    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) return;
        try {
            Duration ttl = Duration.between(Instant.now(), expiresAt);
            if (!ttl.isNegative() && !ttl.isZero()) redis.opsForValue().set(PREFIX + jti, "1", ttl);
        } catch (RuntimeException ignored) { }
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null) return false;
        try { return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti)); }
        catch (RuntimeException e) { return false; }
    }
}
