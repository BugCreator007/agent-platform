package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.domain.repository.AlertRuleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AlertRuleDataSeeder implements ApplicationRunner {
    private final AlertRuleRepository repository;
    public AlertRuleDataSeeder(AlertRuleRepository repository) { this.repository = repository; }
    @Override public void run(ApplicationArguments args) {
        if (!repository.findAll().isEmpty()) return;
        List.of(
                new AlertRule("rule-error", "智能体错误率", "error_rate", "critical", 2, "5m", "企微 + 短信", true),
                new AlertRule("rule-latency", "P95 首响", "p95_latency", "warning", 3, "10m", "企微", true),
                new AlertRule("rule-budget", "月度预算", "budget_used", "warning", 75, "1h", "企微", true),
                new AlertRule("rule-quota", "模型额度", "quota_used", "critical", 90, "1h", "企微 + 短信", true)
        ).forEach(repository::save);
    }
}
