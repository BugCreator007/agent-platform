package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 演示用数据集与文档种子数据初始化器（基础设施层）。
 *
 * <p>首次启动且数据库中没有数据集时，将 {@link KnowledgeSeedData} 中的数据写入持久层；
 * 仅在 {@code app.demo.seed=true}（默认）时生效。顺序为 10，先于分块种子初始化器。</p>
 */
@Component
@Order(10)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class KnowledgeDataSeeder implements ApplicationRunner {
    /** 知识库仓储。 */
    private final KnowledgeRepository repository;
    /**
     * 构造种子初始化器。
     *
     * @param repository 知识库仓储
     */
    public KnowledgeDataSeeder(KnowledgeRepository repository) { this.repository = repository; }
    /**
     * 应用启动后写入种子数据集与文档。
     *
     * @param args 启动参数（未使用）
     */
    @Override public void run(ApplicationArguments args) {
        // 已有数据集说明不是首次启动，直接返回，避免覆盖用户数据
        if (!repository.findAllDatasets().isEmpty()) return;
        KnowledgeSeedData seed = new KnowledgeSeedData();
        seed.findAllDatasets().forEach(repository::saveDataset);
        seed.findAllDatasets().forEach(ds -> seed.findDocuments(ds.id()).forEach(repository::saveDocument));
    }
}
