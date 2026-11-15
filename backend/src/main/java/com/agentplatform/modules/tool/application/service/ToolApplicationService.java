package com.agentplatform.modules.tool.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.tool.domain.model.Tool;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工具目录应用服务（application 层）。
 *
 * <p>提供工具列表、启停、创建与健康检查等管理端用例。关键约束：
 * <ul>
 *   <li>工具名称唯一，重复创建抛出 {@code NAME_CONFLICT}；</li>
 *   <li>创建时对类型、归属、描述提供默认值，保证目录展示完整；</li>
 *   <li>本服务只维护目录元数据，不负责实际执行（执行见 ToolExecutionService）。</li>
 * </ul>
 */
@Service
public class ToolApplicationService {
    /** 工具目录仓储。 */
    private final ToolRepository repository;

    /**
     * 构造应用服务。
     *
     * @param repository 工具目录仓储
     */
    public ToolApplicationService(ToolRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询全部工具。
     *
     * @return 工具列表
     */
    public List<Tool> list() { return repository.findAll(); }

    /**
     * 切换指定工具的启用状态。
     *
     * @param name 工具名称
     * @return 切换后的工具
     * @throws BusinessException 工具不存在（{@code NOT_FOUND}）
     */
    public Tool toggle(String name) {
        Tool tool = require(name);
        tool.toggle();
        repository.save(tool);
        return tool;
    }

    /**
     * 创建不带调用地址的工具（兼容旧调用方式）。
     *
     * @param name        工具名称
     * @param type        工具类型
     * @param owner       归属团队
     * @param description 工具描述
     * @return 创建后的工具
     */
    public Tool create(String name, String type, String owner, String description) {
        return create(name, type, owner, description, null);
    }

    /**
     * 创建工具。
     *
     * <p>名称为必填且不可重复；类型缺省为 {@code HTTP}，归属缺省为“平台团队”，
     * 描述缺省为“自定义工具”，新建工具默认启用且初始指标为零。
     *
     * @param name        工具名称（唯一）
     * @param type        工具类型
     * @param owner       归属团队
     * @param description 工具描述
     * @param endpoint    HTTP 调用地址，可为 {@code null}
     * @return 创建后的工具
     * @throws BusinessException 名称为空（{@code VALIDATION_ERROR}）或已存在（{@code NAME_CONFLICT}）
     */
    public Tool create(String name, String type, String owner, String description, String endpoint) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "工具名称不能为空");
        }
        if (repository.findByName(name).isPresent()) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "工具已存在: " + name);
        }
        Tool tool = new Tool(name, type == null || type.isBlank() ? "HTTP" : type,
                owner == null || owner.isBlank() ? "平台团队" : owner,
                0, 0, "healthy", "刚刚", true,
                description == null || description.isBlank() ? "自定义工具" : description,
                endpoint);
        repository.save(tool);
        return tool;
    }

    /**
     * 对全部工具执行一次健康检查并返回最新列表。
     *
     * <p>注意：当前实现按错误率阈值推断健康状态，并非真实探活。
     *
     * @return 检查后的工具列表
     */
    public List<Tool> checkAll() {
        repository.findAll().forEach(Tool::check);
        return repository.findAll();
    }

    /**
     * 按名称加载工具，不存在时抛出业务异常。
     *
     * @param name 工具名称
     * @return 工具领域对象
     * @throws BusinessException 工具不存在（{@code NOT_FOUND}）
     */
    private Tool require(String name) {
        return repository.findByName(name)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工具不存在: " + name));
    }
}
