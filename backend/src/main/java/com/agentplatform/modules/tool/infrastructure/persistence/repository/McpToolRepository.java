package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpToolMapper;

import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MCP 工具仓储的 MyBatis-Plus 适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@code McpToolRepository} 端口，负责实体与领域对象转换。
 * 批量按键查询在入参为空时直接返回空列表，避免生成非法 SQL。
 */
@Repository
public class McpToolRepository implements com.agentplatform.modules.tool.domain.repository.McpToolRepository {
    /** MyBatis-Plus Mapper。 */
    private final McpToolMapper mapper;

    /**
     * 构造仓储适配器。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public McpToolRepository(McpToolMapper mapper) { this.mapper = mapper; }

    /**
     * 查询某服务下的全部工具。
     *
     * @param serverId 服务主键
     * @return 工具列表
     */
    @Override public List<McpTool> findByServer(String serverId) { return mapper.findByServerId(serverId).stream().map(McpToolEntity::toDomain).toList(); }

    /**
     * 按平台工具键批量查询工具。
     *
     * @param keys 工具键列表
     * @return 命中的工具列表；入参为空时返回空列表
     */
    @Override public List<McpTool> findByKeys(List<String> keys) { return keys.isEmpty() ? List.of() : mapper.findByToolKeyIn(keys).stream().map(McpToolEntity::toDomain).toList(); }

    /**
     * 按平台工具键查询单个工具。
     *
     * @param toolKey 工具键
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    @Override public Optional<McpTool> findByKey(String toolKey) { return Optional.ofNullable(mapper.findByToolKey(toolKey)).map(McpToolEntity::toDomain); }

    /**
     * 按主键查询工具。
     *
     * @param id 工具主键
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    @Override public Optional<McpTool> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(McpToolEntity::toDomain); }

    /**
     * 插入或更新工具。
     *
     * @param tool 工具领域对象
     */
    @Override public void save(McpTool tool) { mapper.insertOrUpdate(McpToolEntity.from(tool)); }

    /**
     * 删除某服务下的全部工具。
     *
     * @param serverId 服务主键
     */
    @Override public void deleteByServer(String serverId) { mapper.deleteByServerId(serverId); }

    /**
     * 按主键删除工具。
     *
     * @param id 工具主键
     */
    @Override public void deleteById(String id) { mapper.deleteById(id); }
}
