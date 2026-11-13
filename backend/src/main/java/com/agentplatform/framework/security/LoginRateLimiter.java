package com.agentplatform.framework.security;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class LoginRateLimiter {
    private static final String PREFIX = "auth:login:fail:";
    private static final int MAX_FAILURES = 5;
    private final StringRedisTemplate redis;
    public LoginRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public void check(String username) {
        try {
            String value = redis.opsForValue().get(PREFIX + username);
            if (value != null && Integer.parseInt(value) >= MAX_FAILURES) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "登录失败次数过多，请 15 分钟后再试");
            }
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException ignored) { }
    }

    public void recordFailure(String username) {
        try {
            Long count = redis.opsForValue().increment(PREFIX + username);
            if (count != null && count == 1) redis.expire(PREFIX + username, Duration.ofMinutes(15));
        } catch (RuntimeException ignored) { }
    }

    public void clear(String username) {
        try { redis.delete(PREFIX + username); } catch (RuntimeException ignored) { }
    }
}
