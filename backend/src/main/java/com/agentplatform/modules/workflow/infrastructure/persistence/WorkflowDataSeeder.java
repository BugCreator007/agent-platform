package com.agentplatform.modules.workflow.infrastructure.persistence;

import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 流程演示数据初始化器。
 *
 * <p>所属层：infrastructure 基础设施层。应用启动完成后执行一次，用于本地/演示环境准备流程定义：</p>
 * <ul>
 *   <li>数据库为空时写入 {@link WorkflowSeedData} 中的全部内置流程；</li>
 *   <li>数据库非空时只补充新增的 {@code wf-weather} 示例（用于演示条件分支与汇聚）。</li>
 * </ul>
 *
 * <p>约束：仅在首次启动或缺少示例时写入，不覆盖用户已修改的数据；可通过
 * {@code app.demo.seed=false} 关闭（默认开启）。本类只用于初始化，不作为运行时存储。</p>
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class WorkflowDataSeeder implements ApplicationRunner {
    /** 流程定义仓储端口，写入种子数据。 */
    private final WorkflowRepository repository;

    /**
     * 构造初始化器。
     *
     * @param repository 流程定义仓储端口
     */
    public WorkflowDataSeeder(WorkflowRepository repository) { this.repository = repository; }

    /**
     * 应用启动后写入种子流程定义。
     *
     * @param args 启动参数（本初始化器不使用）
     */
    @Override public void run(ApplicationArguments args) {
        WorkflowSeedData seed = new WorkflowSeedData();
        if (repository.findAll().isEmpty()) {
            // 全新数据库：一次性写入全部演示流程。
            seed.findAll().forEach(repository::save);
        } else {
            // 已有数据：只补充新增的示例，避免覆盖用户编辑或重复插入。
            seed.findById("wf-weather")
                    .filter(workflow -> repository.findById(workflow.id()).isEmpty())
                    .ifPresent(repository::save);
        }
    }
}
