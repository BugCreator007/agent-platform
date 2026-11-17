package com.agentplatform.modules.insight.application.query;

import java.time.Instant;
import java.util.Map;

/**
 * 事件统计响应（application/query 层）。
 *
 * @param days   统计窗口天数
 * @param from   起始时间（含）
 * @param to     截止时间（不含）
 * @param counts 事件名到数量的映射
 */
public record EventStatsResponse(int days, Instant from, Instant to, Map<String, Long> counts) {
}
