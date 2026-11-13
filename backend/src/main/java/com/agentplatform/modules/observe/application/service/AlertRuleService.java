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

@Service
public class AlertRuleService {
    private static final Logger log = LoggerFactory.getLogger(AlertRuleService.class);
    private final AlertRuleRepository rules;
    private final AlertRepository alerts;
    private final MeterRegistry meters;
    private final CostApplicationService costs;
    private final WebClient webClient;
    private final String wecomWebhook;

    public AlertRuleService(AlertRuleRepository rules, AlertRepository alerts, MeterRegistry meters,
                            CostApplicationService costs, WebClient.Builder builder,
                            @Value("${app.notify.wecom-webhook:}") String wecomWebhook) {
        this.rules = rules; this.alerts = alerts; this.meters = meters; this.costs = costs;
        this.webClient = builder.build();
        this.wecomWebhook = wecomWebhook;
    }

    public List<AlertRule> list() { return rules.findAll(); }

    public AlertRule save(AlertRule rule) {
        rules.save(rule);
        return rule;
    }

    @Scheduled(fixedDelayString = "${app.alert.evaluate-interval-ms:60000}")
    public void evaluate() {
        for (AlertRule rule : rules.findAll()) {
            if (!rule.enabled()) continue;
            double value = metricValue(rule.metric());
            if (value > rule.thresholdValue()) {
                String title = rule.name() + " 超过阈值";
                boolean exists = alerts.findAll().stream().anyMatch(a -> "active".equals(a.status())
                        && a.rule().equals(rule.metric()) && a.title().equals(title));
                if (!exists) {
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

    private double metricValue(String metric) {
        return switch (metric) {
            case "error_rate" -> ratio("agent_runtime_errors_total", "agent_runtime_requests_total");
            case "p95_latency" -> {
                var timer = meters.find("agent_runtime_duration").timer();
                yield timer == null || timer.count() == 0 ? 0 : timer.mean(java.util.concurrent.TimeUnit.SECONDS);
            }
            case "budget_used" -> {
                var summary = costs.summary();
                yield summary.budget() == 0 ? 0 : summary.total() / summary.budget() * 100;
            }
            case "quota_used" -> 0;
            default -> 0;
        };
    }

    private double ratio(String numerator, String denominator) {
        var n = meters.find(numerator).counter();
        var d = meters.find(denominator).counter();
        return n == null || d == null || d.count() == 0 ? 0 : n.count() / d.count() * 100;
    }

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
