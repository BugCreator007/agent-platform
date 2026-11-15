package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.McpTool;

import java.util.List;
import java.util.Optional;

/**
 * MCP 工具仓储端口（domain 层定义，infrastructure 层实现）。
 *
 * <p>按服务、工具键或主键查询同步后的 MCP 工具，并支持批量按键查询以支撑
 * Agent 工具绑定。删除提供按服务级联与按主键两种方式。
 */
public interface McpToolRepository {
    /**
     * 查询某服务下的全部工具。
     *
     * @param serverId 服务主键
     * @return 工具列表；无数据时返回空列表
     */
    List<McpTool> findByServer(String serverId);

    /**
     * 按平台工具键批量查询工具。
     *
     * @param keys 工具键列表
     * @return 命中的工具列表；入参为空时返回空列表
     */
    List<McpTool> findByKeys(List<String> keys);

    /**
     * 按平台工具键查询单个工具。
     *
     * @param toolKey 工具键
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    Optional<McpTool> findByKey(String toolKey);

    /**
     * 按主键查询工具。
     *
     * @param id 工具主键
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    Optional<McpTool> findById(String id);

    /**
     * 保存工具（存在则更新，不存在则插入）。
     *
     * @param tool 待保存的领域对象
     */
    void save(McpTool tool);

    /**
     * 删除某服务下的全部工具（用于删除服务时级联清理）。
     *
     * @param serverId 服务主键
     */
    void deleteByServer(String serverId);

    /**
     * 按主键删除工具。
     *
     * @param id 工具主键
     */
    void deleteById(String id);
}
