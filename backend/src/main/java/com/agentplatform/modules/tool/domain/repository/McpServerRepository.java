package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpServer;

import java.util.List;
import java.util.Optional;

/**
 * MCP 服务仓储端口（domain 层定义，infrastructure 层实现）。
 *
 * <p>提供 MCP 服务聚合根的读写能力；实现负责持久化与鉴权令牌的加密/解密，
 * 领域层不感知存储细节。
 */
public interface McpServerRepository {
    /**
     * 查询全部 MCP 服务。
     *
     * @return 服务列表；无数据时返回空列表
     */
    List<McpServer> findAll();

    /**
     * 按主键查询 MCP 服务。
     *
     * @param id 服务主键
     * @return 服务；不存在时返回 {@link Optional#empty()}
     */
    Optional<McpServer> findById(String id);

    /**
     * 保存服务（存在则更新，不存在则插入）。
     *
     * @param server 待保存的领域对象
     */
    void save(McpServer server);

    /**
     * 按主键删除服务。
     *
     * @param id 服务主键
     */
    void deleteById(String id);
}
