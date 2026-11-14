package com.agentplatform.modules.chat.application.query;

import java.time.Instant;

/**
 * 会话分析读模型：只包含运营指标需要的字段，避免把完整会话（含 trace）读出。
 */
public record SessionFact(
        String agentId, String agentName, String status,
        int tools, int tokens, long ms, Instant createdAt
) {
}
