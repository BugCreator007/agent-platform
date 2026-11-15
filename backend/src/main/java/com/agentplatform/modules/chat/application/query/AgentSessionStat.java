package com.agentplatform.modules.chat.application.query;

/**
 * 单个智能体在某个时间窗口内的真实运行指标（应用层读模型）。
 *
 * @param agentId   智能体 ID
 * @param agentName 智能体展示名称；数据中缺失时回退为智能体 ID
 * @param sessions  时间窗口内该智能体的会话总数
 * @param success   成功率（百分比，保留一位小数）；没有终态会话时为 0
 * @param p95       终态会话耗时的 P95，格式化为 "x.xxs" 或 "xxx ms"；无耗时样本时为 "—"
 * @param tools     时间窗口内该智能体累计的工具调用次数
 */
public record AgentSessionStat(
        String agentId, String agentName, long sessions, double success, String p95, long tools
) {
}
