package com.agentplatform.framework.security;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 登录失败限流器。
 *
 * <p>位于技术框架的安全层，基于 Redis 计数器防止账号密码被暴力破解：
 * 同一用户名 15 分钟内失败达到 5 次即拒绝继续尝试。
 * 采用「失败计数 + 固定窗口」策略，登录成功后清零。</p>
 */
@Service
public class LoginRateLimiter {
    /** Redis 键前缀，按用户名隔离计数 */
    private static final String PREFIX = "auth:login:fail:";
    /** 窗口内允许的最大失败次数 */
    private static final int MAX_FAILURES = 5;
    private final StringRedisTemplate redis;

    /**
     * @param redis Redis 字符串模板
     */
    public LoginRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    /**
     * 检查用户名是否已被锁定。
     *
     * <p>Redis 不可用时选择「放行」（fail-open），避免缓存故障导致所有人无法登录；
     * 业务异常需原样抛出，不能被吞掉。</p>
     *
     * @param username 登录用户名
     * @throws BusinessException 失败次数超限时抛出（403）
     */
    public void check(String username) {
        try {
            String value = redis.opsForValue().get(PREFIX + username);
            if (value != null && Integer.parseInt(value) >= MAX_FAILURES) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "登录失败次数过多，请 15 分钟后再试");
            }
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException ignored) { }
    }

    /**
     * 记录一次登录失败。
     *
     * <p>首次失败时设置 15 分钟过期，形成固定窗口；后续失败只自增。
     * Redis 异常同样忽略，不阻断登录流程。</p>
     *
     * @param username 登录用户名
     */
    public void recordFailure(String username) {
        try {
            Long count = redis.opsForValue().increment(PREFIX + username);
            if (count != null && count == 1) redis.expire(PREFIX + username, Duration.ofMinutes(15));
        } catch (RuntimeException ignored) { }
    }

    /**
     * 登录成功后清除失败计数。
     *
     * @param username 登录用户名
     */
    public void clear(String username) {
        try { redis.delete(PREFIX + username); } catch (RuntimeException ignored) { }
    }
}
