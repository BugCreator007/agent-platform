package com.agentplatform.modules.observe.application.service;

import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import com.agentplatform.modules.observe.domain.repository.AlertRuleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * 告警规则服务（应用层）。
 *
 * <p>按固定间隔（默认 60s）扫描启用的告警规则，从 Micrometer 指标与成本汇总中取当前值，
 * 超过阈值时生成告警并推送企业微信 Webhook。去重规则：同一规则、同一标题且仍为
 * active 的告警存在时不再重复创建，避免每次评估都刷屏。</p>
 *
 * <p>注意：评估任务与 Webhook 通知均为尽力而为，通知失败只记录 warn 日志，
 * 不影响告警落库。</p>
 */
@Service
public class AlertRuleService {
    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);
    private final AlertRuleRepository rules;
    private final AlertRepository alerts;
    private final MeterRegistry meters;
    private final CostApplicationService costs;
    private final WebClient webClient;
    private final String wecomWebhook;

    /**
     * 构造告警规则服务。
     *
     * @param rules        告警规则仓储
     * @param alerts       告警仓储
     * @param meters       Micrometer 指标注册表
     * @param costs        成本服务（用于预算使用率）
     * @param builder      WebClient 构建器
     * @param wecomWebhook 企业微信机器人 Webhook 地址，未配置时为空
     */
    public AlertRuleService(AlertRuleRepository rules, AlertRepository alerts, MeterRegistry meters,
                            CostApplicationService costs, WebClient.Builder builder,
                            @Value("${app.notify.wecom-webhook:}") String wecomWebhook) {
        this.rules = rules; this.alerts = alerts; this.meters = meters; this.costs = costs;
        this.webClient = builder.build();
        this.wecomWebhook = wecomWebhook;
    }

    /**
     * 查询全部告警规则。
     *
     * @return 告警规则列表
     */
    public List<AlertRule> list() { return rules.findAll(); }

    /**
     * 保存（新增或更新）告警规则。
     *
     * @param rule 告警规则
     * @return 保存后的规则
     */
    public AlertRule save(AlertRule rule) {
        rules.save(rule);
        return rule;
    }

    /**
     * 定时评估全部启用的告警规则。
     *
     * <p>执行间隔由 {@code app.alert.evaluate-interval-ms} 配置（默认 60000ms），
     * 使用 fixedDelay 保证上一次评估结束后再开始下一次。</p>
     */
    @Scheduled(fixedDelayString = "${app.alert.evaluate-interval-ms:60000}")
    public void evaluate() {
        for (AlertRule rule : rules.findAll()) {
            if (!rule.enabled()) continue;
            double value = metricValue(rule.metric());
            if (value > rule.thresholdValue()) {
                String title = rule.name() + " 超过阈值";
                // 去重：同一规则+标题仍有 active 告警时跳过，防止每个评估周期重复告警
                boolean exists = alerts.findAll().stream().anyMatch(a -> "active".equals(a.status())
                        && a.rule().equals(rule.metric()) && a.title().equals(title));
                if (!exists) {
                    // 时间按服务器本地时区展示（控制台补充“刚刚”相对时间）
                    Alert alert = new Alert("al_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                            rule.level(), title, rule.metric(), "平台监控", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
                            "刚刚", "active", String.format("%.2f / %.2f", value, rule.thresholdValue()),
                            "规则 " + rule.name() + " 触发，当前值 " + value + "，阈值 " + rule.thresholdValue());
                    alerts.save(alert);
                    notifyWecom(rule, alert);
                }
            }
        }
    }

    /**
     * 读取规则指标当前值。
     *
     * <p>error_rate 与 p95_latency 来自运行时 Micrometer 指标，
     * budget_used 来自成本汇总；quota_used 暂无数据源，返回 0（不会触发）。</p>
     *
     * @param metric 指标名
     * @return 指标当前值（百分比或秒数），未知指标返回 0
     */
    private double metricValue(String metric) {
        return switch (metric) {
            // 错误率 = 错误计数 / 请求计数 * 100
            case "error_rate" -> ratio("agent_runtime_errors_total", "agent_runtime_requests_total");
            case "p95_latency" -> {
                var timer = meters.find("agent_runtime_duration").timer();
                // 注意：此处使用 timer.mean 近似平均耗时，并非真正的 P95 分位数
                yield timer == null || timer.count() == 0 ? 0 : timer.mean(java.util.concurrent.TimeUnit.SECONDS);
            }
            case "budget_used" -> {
                var summary = costs.summary();
                // 预算为 0 时避免除零，返回 0
                yield summary.budget() == 0 ? 0 : summary.total() / summary.budget() * 100;
            }
            case "quota_used" -> 0;
            default -> 0;
        };
    }

    /**
     * 计算两个计数器的百分比比值。
     *
     * @param numerator   分子计数器名
     * @param denominator 分母计数器名
     * @return 百分比；任一计数器缺失或分母为 0 时返回 0
     */
    private double ratio(String numerator, String denominator) {
        var n = meters.find(numerator).counter();
        var d = meters.find(denominator).counter();
        return n == null || d == null || d.count() == 0 ? 0 : n.count() / d.count() * 100;
    }

    /**
     * 通过企业微信机器人推送 markdown 告警。
     *
     * <p>未配置 Webhook 时只记录 info 日志；通知为异步订阅，失败仅记录 warn，
     * 不阻塞评估任务。</p>
     *
     * @param rule  触发的规则
     * @param alert 生成的告警
     */
    private void notifyWecom(AlertRule rule, Alert alert) {
        if (wecomWebhook == null || wecomWebhook.isBlank()) {
            log.info("alert {} triggered; WeCom webhook not configured", alert.title());
            return;
        }
        webClient.post().uri(wecomWebhook).bodyValue(java.util.Map.of("msgtype", "markdown", "markdown",
                java.util.Map.of("content", "**" + alert.title() + "**\n> " + alert.detail()))).retrieve()
                .toBodilessEntity().subscribe(null, e -> log.warn("wecom notify failed: {}", e.getMessage()));
    }
}
