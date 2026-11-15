package com.agentplatform.modules.agent.domain.repository;

import com.agentplatform.modules.agent.domain.model.Agent;

import java.util.List;
import java.util.Optional;

/**
 * 智能体仓储端口（domain 层）。
 * <p>只定义持久化抽象，实现位于 infrastructure 层（MyBatis-Plus）。
 * 应用层依赖此接口而非具体实现，便于替换存储与测试。</p>
 */
public interface AgentRepository {
    /**
     * 按主键查询智能体。
     *
     * @param id 智能体唯一标识
     * @return 存在时返回聚合，否则返回空 Optional
     */
    Optional<Agent> findById(String id);

    /**
     * 按业务编码查询，用于创建时校验编码唯一性。
     *
     * @param code 业务编码
     * @return 存在时返回聚合，否则返回空 Optional
     */
    Optional<Agent> findByCode(String code);

    /**
     * 查询全部智能体（项目演示规模，未做分页）。
     *
     * @return 全部智能体列表
     */
    List<Agent> findAll();

    /**
     * 新增或更新聚合，具体 upsert 语义由实现决定。
     *
     * @param agent 待持久化的聚合
     */
    void save(Agent agent);

    /**
     * 按主键删除；不存在的 id 视为幂等成功。
     *
     * @param id 智能体唯一标识
     */
    void deleteById(String id);
}
