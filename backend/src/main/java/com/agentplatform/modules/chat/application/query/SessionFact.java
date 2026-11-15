package com.agentplatform.modules.chat.application.query;

import java.time.Instant;

/**
 * 会话分析读模型：只包含运营指标需要的字段，避免把完整会话（含 trace）读出。
 *
 * @param agentId   智能体 ID
 * @param agentName 智能体展示名称
 * @param status    会话终态/运行态：success、failed、timeout、error、running 等
 * @param tools     该会话的工具调用次数
 * @param tokens    该会话消耗的 token 估算值
 * @param ms        会话耗时（毫秒）
 * @param createdAt 会话创建时间
 */
public record SessionFact(
        String agentId, String agentName, String status,
        int tools, int tokens, long ms, Instant createdAt
) {
}
