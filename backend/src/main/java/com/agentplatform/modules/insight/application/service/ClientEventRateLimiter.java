package com.agentplatform.modules.insight.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端事件限流器（application 层辅助组件）。
 *
 * <p>同一用户每分钟最多 120 次客户端事件上报，优先使用 Redis 固定窗口；
 * Redis 不可用时退化为单机内存窗口。超限返回 429，不阻断页面主流程。</p>
 */
@Service
public class ClientEventRateLimiter {
    /** 每分钟允许的事件数。 */
    private static final int MAX_PER_MINUTE = 120;
    /** Redis 键前缀。 */
    private static final String PREFIX = "event:rate:";
    /** 内存窗口长度。 */
    private static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redis;
    private final Map<String, Deque<Long>> memoryWindows = new ConcurrentHashMap<>();

    /**
     * @param redis Redis 字符串模板；允许为 null（测试环境走内存窗口）
     */
    public ClientEventRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 检查并记录一次客户端事件。
     *
     * @param userId 当前登录用户
     * @throws BusinessException 超限时抛出 429
     */
    public void check(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            String key = PREFIX + userId + ":" + (System.currentTimeMillis() / WINDOW_MS);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(1));
            if (count != null && count > MAX_PER_MINUTE) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "事件上报过于频繁");
            }
            return;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // Redis 故障时放行到内存窗口，避免埋点问题阻断用户操作。
        }
        checkMemory(userId);
    }

    /**
     * 单机固定窗口计数。
     *
     * @param userId 当前登录用户
     */
    private void checkMemory(String userId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = memoryWindows.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_PER_MINUTE) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "事件上报过于频繁");
            }
            window.addLast(now);
        }
    }
}
