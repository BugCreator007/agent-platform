package com.agentplatform.modules.observe.application.query;

import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;

/**
 * 运营概览：所有数值来自真实会话、告警与发布数据，时间按 Asia/Shanghai 统计。
 *
 * @param kpi      顶部 KPI 卡片（今日会话、成功率、P95、本月成本及其环比/同比）
 * @param pulse    实时脉搏（运行中会话、在线智能体、错误率、P99、峰值 RPM、近 60 分钟吞吐）
 * @param series   趋势序列（按天：会话数、工具调用、成功率、P95、成本）
 * @param snapshot 生成时间与统计天数
 * @param agents   各智能体的运营指标
 * @param alerts   告警列表
 * @param releases 发布记录列表
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
    /**
     * 顶部 KPI 指标。
     *
     * @param todaySessions 今日会话数（格式化字符串）
     * @param sessionsDelta 会话数较昨日变化
     * @param successRate   今日成功率，无已完成会话时 “—”
     * @param successDelta  成功率较昨日变化（百分点）
     * @param p95           今日 P95 响应耗时
     * @param p95Delta      P95 较昨日变化（秒，正值表示变慢）
     * @param cost          本月成本
     * @param costDelta     本月成本较上月同期变化
     */
    public record Kpi(String todaySessions, String sessionsDelta, String successRate, String successDelta,
                      String p95, String p95Delta, String cost, String costDelta) {
    }

    /**
     * 实时脉搏指标。
     *
     * @param runningSessions   近半小时内运行中会话数
     * @param onlineAgents      running/grayscale 状态的智能体数
     * @param errorRate         近 1 小时错误率
     * @param p99               近 1 小时 P99 耗时
     * @param peakRpm           近 60 分钟每分钟峰值会话数
     * @param throughput        近 60 分钟逐分钟会话数
     * @param throughputLabels  与 throughput 对应的 HH:mm 标签
     */
    public record Pulse(long runningSessions, long onlineAgents, String errorRate, String p99,
                        long peakRpm, List<Long> throughput, List<String> throughputLabels) {
    }

    /**
     * 按天趋势序列，各列表下标一一对应。
     *
     * @param labels  日期标签（MM-dd）
     * @param sessions 每日会话数
     * @param tools   每日工具调用数
     * @param success 每日成功率
     * @param p95     每日 P95 耗时（毫秒）
     * @param cost    每日估算成本
     */
    public record Series(List<String> labels, List<Long> sessions, List<Long> tools,
                         List<Double> success, List<Long> p95, List<Double> cost) {
    }

    /**
     * 快照元信息。
     *
     * @param generatedAt 生成时间（Asia/Shanghai，yyyy-MM-dd HH:mm:ss）
     * @param rangeDays   统计天数
     */
    public record Snapshot(String generatedAt, int rangeDays) {
    }
}
