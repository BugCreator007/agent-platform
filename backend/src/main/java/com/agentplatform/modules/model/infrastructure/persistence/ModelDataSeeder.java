package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.domain.repository.ModelRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 模型模块种子数据初始化器（infrastructure 层）。
 *
 * <p>仅在 {@code app.demo.seed} 为 true（默认）时执行，且只用于首次启动初始化：
 * 厂商/模型表为空时写入完整演示目录；已有数据时只增量补齐新增的厂商与模型，
 * 不复活用户删除的条目。最后在 DeepSeek 未配置密钥时尝试用配置项回填。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class ModelDataSeeder implements ApplicationRunner {
    /** 后续版本新增的模型：已有库只增量补齐这些条目。 */
    private static final Set<String> INCREMENTAL_MODELS = Set.of(
            "qwen3-coder-plus", "qwen-vl-max", "kimi-k2.5", "kimi-k2-turbo", "moonshot-v1-128k");
    private final ModelRepository repository;
    private final String deepSeekKey;

    /**
     * 构造种子初始化器。
     *
     * @param repository  模型模块领域仓储端口
     * @param deepSeekKey 配置中的 DeepSeek API Key，可为空
     */
    public ModelDataSeeder(ModelRepository repository,
                           @Value("${app.ai.deepseek.api-key:}") String deepSeekKey) {
        this.repository = repository;
        this.deepSeekKey = deepSeekKey == null ? "" : deepSeekKey.trim();
    }

    /**
     * 应用启动后执行种子初始化。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        ModelSeedData seed = new ModelSeedData();
        if (repository.findProviders().isEmpty()) {
            // 空库：写入完整演示目录与默认路由策略
            seed.findProviders().forEach(repository::saveProvider);
            seed.findModels().forEach(repository::saveModel);
            repository.saveRouting(seed.routing());
        } else {
            // Add newly introduced providers/models without resurrecting user-deleted catalog entries.
            seed.findProvider("kimi").filter(p -> repository.findProvider(p.id()).isEmpty())
                    .ifPresent(repository::saveProvider);
            seed.findModels().stream()
                    .filter(m -> INCREMENTAL_MODELS.contains(m.id()))
                    .filter(m -> repository.findModel(m.id()).isEmpty())
                    .forEach(repository::saveModel);
        }
        // Existing local installs keep working from .env until an explicit key is stored in the console.
        // 仅当控制台尚未保存密钥时才回填环境变量，避免覆盖用户显式配置
        repository.findProvider("deepseek").ifPresent(provider -> {
            if (!provider.keyConfigured() && !deepSeekKey.isBlank()) {
                provider.updateKey(deepSeekKey);
                repository.saveProvider(provider);
            }
        });
    }
}
