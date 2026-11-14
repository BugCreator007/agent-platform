package com.agentplatform.modules.observe.application.query;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;

/**
 * 运营概览：所有数值来自真实会话、告警与发布数据，时间按 Asia/Shanghai 统计。
 */
public record OverviewResponse(
        Kpi kpi,
        Pulse pulse,
        Series series,
        Snapshot snapshot,
        List<AgentSummary> agents,
        List<Alert> alerts,
        List<Release> releases
) {
    public record Kpi(String todaySessions, String sessionsDelta, String successRate, String successDelta,
                      String p95, String p95Delta, String cost, String costDelta) {
    }

    public record Pulse(long runningSessions, long onlineAgents, String errorRate, String p99,
                        long peakRpm, List<Long> throughput, List<String> throughputLabels) {
    }

    public record Series(List<String> labels, List<Long> sessions, List<Long> tools,
                         List<Double> success, List<Long> p95, List<Double> cost) {
    }

    public record Snapshot(String generatedAt, int rangeDays) {
    }
}
