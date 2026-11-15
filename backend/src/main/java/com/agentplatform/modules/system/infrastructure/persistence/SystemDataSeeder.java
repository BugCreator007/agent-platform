package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.domain.repository.SystemRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 平台系统演示数据初始化器。
 *
 * <p>位于基础设施层，应用启动时执行一次：仅当成员表为空时写入种子数据
 * （成员、API Key、审计日志），避免覆盖已有环境数据。
 * 由 {@code app.demo.seed=false} 关闭（生产环境应关闭）。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class SystemDataSeeder implements ApplicationRunner {
    private final SystemRepository repository;

    /**
     * @param repository 平台系统仓储端口
     */
    public SystemDataSeeder(SystemRepository repository) { this.repository = repository; }

    /**
     * 启动时执行种子数据写入。
     *
     * @param args 应用启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        // 已有成员数据说明不是首次启动，直接跳过
        if (!repository.members().isEmpty()) return;
        SystemSeedData seed = new SystemSeedData();
        seed.members().forEach(repository::saveMember);
        seed.keys().forEach(repository::saveKey);
        seed.audit().forEach(repository::saveAudit);
    }
}
