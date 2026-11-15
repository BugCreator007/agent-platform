package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.domain.repository.ReleaseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 发布记录演示数据初始化器（infrastructure 层）。
 * <p>仅在 {@code app.demo.seed=true}（缺省开启）且 releases 表为空时写入种子数据，
 * 避免覆盖已有发布记录。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ReleaseDataSeeder implements ApplicationRunner {
    private final ReleaseRepository repository;

    /**
     * @param repository 发布记录仓储端口
     */
    public ReleaseDataSeeder(ReleaseRepository repository) { this.repository = repository; }

    /**
     * 应用启动后检查表是否为空，为空则批量写入演示发布记录。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        if (repository.findAll().isEmpty()) new ReleaseSeedData().findAll().forEach(repository::save);
    }
}
