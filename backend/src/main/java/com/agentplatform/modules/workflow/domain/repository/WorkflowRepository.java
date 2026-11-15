package com.agentplatform.modules.workflow.domain.repository;

import com.agentplatform.modules.workflow.domain.model.Workflow;

import java.util.List;
import java.util.Optional;

/**
 * 流程定义仓储端口（domain 层）。
 *
 * <p>由 infrastructure 层的 MyBatis-Plus 适配器实现；application 层仅依赖本接口，
 * 从而保持领域层与持久化技术解耦。</p>
 */
public interface WorkflowRepository {
    /**
     * 查询全部流程定义。
     *
     * @return 流程定义列表
     */
    List<Workflow> findAll();

    /**
     * 按 ID 查询流程定义。
     *
     * @param id 流程 ID
     * @return 流程定义（可能为空）
     */
    Optional<Workflow> findById(String id);

    /**
     * 新增或更新流程定义（按主键存在与否决定）。
     *
     * @param workflow 流程定义
     */
    void save(Workflow workflow);
}
