package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 工具目录种子数据初始化器（infrastructure 层）。
 *
 * <p>仅在 {@code app.demo.seed=true}（缺省开启）时生效，用于首次启动填充演示工具。
 * 关键约束：种子数据只做初始化，不覆盖已有数据；目录非空时仅补种
 * {@code weather.query} 这一增量工具，避免重复写入或回滚用户修改。
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ToolDataSeeder implements ApplicationRunner {
    /** 工具目录仓储。 */
    private final ToolRepository repository;

    /**
     * 构造初始化器。
     *
     * @param repository 工具目录仓储
     */
    public ToolDataSeeder(ToolRepository repository) { this.repository = repository; }

    /**
     * 应用启动后执行：空目录全量写入种子数据，非空目录仅补齐新增的
     * {@code weather.query}。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        ToolSeedData seed = new ToolSeedData();
        if (repository.findAll().isEmpty()) {
            seed.findAll().forEach(repository::save);
        } else {
            // 目录已有数据时不整体覆盖，只补充后续版本新增的种子条目。
            seed.findByName("weather.query")
                    .filter(tool -> repository.findByName(tool.name()).isEmpty())
                    .ifPresent(repository::save);
        }
    }
}
