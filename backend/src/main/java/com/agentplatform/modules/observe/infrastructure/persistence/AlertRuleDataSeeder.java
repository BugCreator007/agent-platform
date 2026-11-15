package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import com.agentplatform.modules.observe.domain.repository.AlertRuleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告警规则演示种子初始化器（infrastructure 层）。
 *
 * <p>仅在 {@code app.demo.seed} 开启（默认）且规则表为空时写入四条默认规则，
 * 覆盖错误率、P95 延迟、月度预算与模型额度四类指标。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AlertRuleDataSeeder implements ApplicationRunner {
    private final AlertRuleRepository repository;

    /**
     * 构造初始化器。
     *
     * @param repository 告警规则仓储端口
     */
    public AlertRuleDataSeeder(AlertRuleRepository repository) { this.repository = repository; }

    /**
     * 应用启动后写入默认告警规则（规则表为空时）。
     *
     * <p>规则中的 metric 必须与 {@code AlertRuleService} 的指标解析逻辑对应，
     * 否则评估结果恒为 0 不会触发。</p>
     *
     * @param args 启动参数（未使用）
     */
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
