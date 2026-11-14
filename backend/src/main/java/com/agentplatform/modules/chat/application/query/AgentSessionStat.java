package com.agentplatform.modules.chat.application.query;

/**
 * 单个智能体在某个时间窗口内的真实运行指标。
 */
public record AgentSessionStat(
        String agentId, String agentName, long sessions, double success, String p95, long tools
) {
}
