package com.agentplatform.modules.observe.infrastructure.persistence;

import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 告警演示种子数据初始化器（infrastructure 层）。
 *
 * <p>仅在 {@code app.demo.seed} 开启（默认）且告警表为空时写入演示告警，
 * 只用于首次启动，不覆盖运行中产生的数据。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AlertDataSeeder implements ApplicationRunner {
    private final AlertRepository repository;

    /**
     * 构造初始化器。
     *
     * @param repository 告警仓储端口
     */
    public AlertDataSeeder(AlertRepository repository) { this.repository = repository; }

    /**
     * 应用启动后写入演示告警（表为空时）。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new AlertSeedData().findAll().forEach(repository::save);
    }
}
