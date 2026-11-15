package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.Tool;

import java.util.List;
import java.util.Optional;

/**
 * 工具目录仓储端口（domain 层定义，infrastructure 层实现）。
 *
 * <p>工具以名称作为唯一标识；仓储只负责持久化，不校验业务约束（如名称冲突），
 * 约束由应用服务保证。
 */
public interface ToolRepository {
    /**
     * 查询全部工具。
     *
     * @return 工具列表；无数据时返回空列表
     */
    List<Tool> findAll();

    /**
     * 按名称查询工具。
     *
     * @param name 工具名称
     * @return 工具；不存在时返回 {@link Optional#empty()}
     */
    Optional<Tool> findByName(String name);

    /**
     * 保存工具（存在则更新，不存在则插入）。
     *
     * @param tool 待保存的领域对象
     */
    void save(Tool tool);

    /**
     * 按名称删除工具。
     *
     * @param name 工具名称
     */
    void deleteByName(String name);
}
