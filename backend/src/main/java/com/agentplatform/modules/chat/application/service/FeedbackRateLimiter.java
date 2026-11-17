package com.agentplatform.modules.chat.application.service;

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
 * 反馈写操作限流器（application 层辅助组件）。
 *
 * <p>同一用户每分钟最多 20 次反馈写操作，优先使用 Redis 固定窗口计数；
 * Redis 不可用时退化为单机内存窗口，保证限流不会阻断反馈主流程。
 * 只做写入限流，读取反馈不受影响。</p>
 */
@Service
public class FeedbackRateLimiter {
    /** 每分钟允许的写操作数。 */
    private static final int MAX_PER_MINUTE = 20;
    /** Redis 键前缀。 */
    private static final String PREFIX = "feedback:rate:";
    /** Redis 不可用时的单机窗口。 */
    private static final long WINDOW_MS = 60_000L;

    private final StringRedisTemplate redis;
    private final Map<String, Deque<Long>> memoryWindows = new ConcurrentHashMap<>();

    /**
     * @param redis Redis 字符串模板；允许为 null（测试或未接入 Redis 时走内存窗口）
     */
    public FeedbackRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 检查并记录一次反馈写操作。
     *
     * @param userId 当前登录用户
     * @throws BusinessException 超过每分钟上限时抛出 429
     */
    public void check(String userId) {
        if (userId == null || userId.isBlank()) return;
        try {
            String key = PREFIX + userId + ":" + (System.currentTimeMillis() / WINDOW_MS);
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(1));
            if (count != null && count > MAX_PER_MINUTE) {
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "反馈过于频繁，请稍后再试");
            }
            return;
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException ignored) {
            // Redis 故障时退化为单机窗口，避免缓存问题阻断反馈。
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
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "反馈过于频繁，请稍后再试");
            }
            window.addLast(now);
        }
    }
}
