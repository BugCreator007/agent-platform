package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.ToolMapper;

import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.ToolEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 工具目录仓储的 MyBatis-Plus 适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@code ToolRepository} 端口。工具名称即主键，因此按名称查询/
 * 删除直接使用 {@code selectById}/{@code deleteById}。
 */
@Repository
public class ToolRepository implements com.agentplatform.modules.tool.domain.repository.ToolRepository {
    /** MyBatis-Plus Mapper。 */
    private final ToolMapper mapper;

    /**
     * 构造仓储适配器。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public ToolRepository(ToolMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部工具。
     *
     * @return 工具列表
     */
    @Override public List<Tool> findAll() { return mapper.selectList(null).stream().map(ToolEntity::toDomain).toList(); }

    /**
     * 按名称查询工具（名称即主键）。
     *
     * @param name 工具名称
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    @Override public Optional<Tool> findByName(String name) { return Optional.ofNullable(mapper.selectById(name)).map(ToolEntity::toDomain); }

    /**
     * 插入或更新工具。
     *
     * @param tool 工具领域对象
     */
    @Override public void save(Tool tool) { mapper.insertOrUpdate(ToolEntity.from(tool)); }

    /**
     * 按名称删除工具（名称即主键）。
     *
     * @param name 工具名称
     */
    @Override public void deleteByName(String name) { mapper.deleteById(name); }
}
