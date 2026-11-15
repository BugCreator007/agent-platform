package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 智能体模型绑定补齐器（infrastructure 层）。
 * <p>用于兼容历史数据：早期种子/存量记录可能只有模型展示名而没有
 * {@code providerId}/{@code modelId}，启动时根据名称关键字补齐绑定。
 * 通过 {@code @Order(30)} 显式排序（未标注 {@code @Order} 的 Runner 优先级最低，
 * 排在其后执行），因此本类主要处理库中已存在但未绑定的历史记录；
 * 种子数据在 {@link AgentSeedData} 构造时已经完成绑定。已经绑定过的记录不会重复处理。</p>
 */
@Component
@Order(30)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class AgentBindingSeeder implements ApplicationRunner {
    private final AgentRepository repository;

    /**
     * @param repository 智能体仓储端口实现
     */
    public AgentBindingSeeder(AgentRepository repository) { this.repository = repository; }

    /**
     * 扫描全部智能体，为缺少 providerId 的记录尝试推断并回写模型绑定。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        repository.findAll().forEach(agent -> {
            // 仅处理未绑定模型的记录，避免覆盖页面显式配置的厂商/模型
            if (agent.providerId() == null) {
                AgentModelBinder.bind(agent);
                // 只有成功推断出 providerId 时才回写，减少无意义更新
                if (agent.providerId() != null) repository.save(agent);
            }
        });
    }
}
