package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 智能体演示数据初始化器（infrastructure 层）。
 * <p>仅在 {@code app.demo.seed=true}（缺省即开启）且 agents 表为空时写入种子数据，
 * 避免应用重启时覆盖生产或用户已维护的数据。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AgentDataSeeder implements ApplicationRunner {
    private final AgentRepository repository;

    /**
     * @param repository 智能体仓储端口实现
     */
    public AgentDataSeeder(AgentRepository repository) { this.repository = repository; }

    /**
     * 应用启动后检查表是否为空，为空则批量写入演示智能体。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new AgentSeedData().findAll().forEach(repository::save);
    }
}
