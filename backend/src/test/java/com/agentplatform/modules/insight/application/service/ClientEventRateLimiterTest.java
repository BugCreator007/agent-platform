package com.agentplatform.modules.insight.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 客户端事件限流器单元测试：Redis 不可用时按内存窗口限制 120 次/分钟。
 */
class ClientEventRateLimiterTest {

    /** 前 120 次通过，第 121 次返回 429。 */
    @Test
    void limitsTo120PerMinuteWithoutRedis() {
        ClientEventRateLimiter limiter = new ClientEventRateLimiter(null);
        for (int i = 0; i < 120; i++) {
            assertDoesNotThrow(() -> limiter.check("event_user"));
        }
        BusinessException error = assertThrows(BusinessException.class,
                () -> limiter.check("event_user"));
        assertEquals(ErrorCode.TOO_MANY_REQUESTS, error.errorCode());
    }
}
