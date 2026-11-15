package com.agentplatform.modules.tool.infrastructure.persistence.repository;

import com.agentplatform.modules.tool.infrastructure.persistence.mapper.McpToolCallMapper;

import com.agentplatform.modules.tool.domain.model.McpToolCall;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpToolCallEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * MCP 调用审计仓储的 MyBatis-Plus 适配器（infrastructure 层）。
 *
 * <p>实现 domain 层 {@code McpToolCallRepository} 端口。查询先固定取最近 50 条，
 * 再按调用方传入的 limit 截断，数据库侧不接收可变 limit。
 */
@Repository
public class McpToolCallRepository implements com.agentplatform.modules.tool.domain.repository.McpToolCallRepository {
    /** MyBatis-Plus Mapper。 */
    private final McpToolCallMapper mapper;

    /**
     * 构造仓储适配器。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    public McpToolCallRepository(McpToolCallMapper mapper) { this.mapper = mapper; }

    /**
     * 插入或更新调用记录（审计记录通常为新增）。
     *
     * @param call 调用记录
     */
    @Override public void save(McpToolCall call) { mapper.insertOrUpdate(McpToolCallEntity.from(call)); }

    /**
     * 查询某服务最近的调用记录，按创建时间倒序。
     *
     * @param serverId 服务主键
     * @param limit    最多返回条数（超过 50 时最多返回 50）
     * @return 调用记录列表
     */
    @Override public List<McpToolCall> recent(String serverId, int limit) {
        return mapper.findTop50ByServerIdOrderByCreatedAtDesc(serverId).stream().limit(limit).map(McpToolCallEntity::toDomain).toList();
    }
}
