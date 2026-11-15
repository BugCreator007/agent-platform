package com.agentplatform.modules.agent.infrastructure.persistence.repository;

import com.agentplatform.modules.agent.infrastructure.persistence.mapper.AgentMapper;

import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.infrastructure.persistence.entity.AgentEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 智能体仓储适配器（infrastructure 层）。
 * <p>实现 domain 层的 {@link com.agentplatform.modules.agent.domain.repository.AgentRepository}
 * 端口，把仓储调用委托给 MyBatis-Plus Mapper，并在实体与领域聚合之间做转换。
 * {@code save} 使用 {@code insertOrUpdate} 实现 upsert，依赖主键 {@code IdType.INPUT}。</p>
 */
@Repository
public class AgentRepository implements com.agentplatform.modules.agent.domain.repository.AgentRepository {
    private final AgentMapper mapper;

    /**
     * @param mapper agents 表 Mapper
     */
    public AgentRepository(AgentMapper mapper) { this.mapper = mapper; }

    /** {@inheritDoc} */
    @Override public Optional<Agent> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(AgentEntity::toDomain); }
    /** {@inheritDoc} */
    @Override public Optional<Agent> findByCode(String code) { return Optional.ofNullable(mapper.findByCode(code)).map(AgentEntity::toDomain); }
    /** {@inheritDoc} */
    @Override public List<Agent> findAll() { return mapper.selectList(null).stream().map(AgentEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public void save(Agent agent) { mapper.insertOrUpdate(AgentEntity.from(agent)); }
    /** {@inheritDoc} */
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
