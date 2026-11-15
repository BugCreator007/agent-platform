package com.agentplatform.modules.observe.domain.model;

/**
 * 告警规则（domain 层值对象）。
 *
 * <p>规则标识一个监控指标及其阈值：定时评估时若指标当前值大于
 * {@code thresholdValue} 则触发告警。{@code metric} 取值需与
 * {@code AlertRuleService.metricValue} 支持的指标名一致。</p>
 *
 * @param id              规则 ID
 * @param name            规则名称（同时用于告警标题）
 * @param metric          指标名：error_rate/p95_latency/budget_used/quota_used
 * @param level           告警级别：critical/warning/info
 * @param thresholdValue  触发阈值
 * @param windowLabel     统计窗口展示值，如 “5m”“1h”
 * @param channel         通知渠道展示值，如 “企微 + 短信”
 * @param enabled         是否启用评估
 */
public record AlertRule(String id, String name, String metric, String level, double thresholdValue,
                        String windowLabel, String channel, boolean enabled) {
}
