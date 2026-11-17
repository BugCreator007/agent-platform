package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 反馈限流器单元测试：Redis 不可用时按内存窗口限制 20 次/分钟。
 */
class FeedbackRateLimiterTest {

    /** 前 20 次通过，第 21 次返回 429。 */
    @Test
    void limitsToTwentyPerMinuteWithoutRedis() {
        FeedbackRateLimiter limiter = new FeedbackRateLimiter(null);
        for (int i = 0; i < 20; i++) {
            assertDoesNotThrow(() -> limiter.check("rate_user"));
        }
        BusinessException error = assertThrows(BusinessException.class,
                () -> limiter.check("rate_user"));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, error.errorCode());
    }
}
