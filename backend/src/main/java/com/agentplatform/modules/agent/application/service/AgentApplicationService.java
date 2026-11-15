package com.agentplatform.modules.agent.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.agent.application.query.AgentSummary;
import com.agentplatform.modules.agent.domain.model.Agent;
import com.agentplatform.modules.agent.domain.repository.AgentRepository;
import com.agentplatform.modules.agent.interfaces.dto.CreateAgentRequest;
import com.agentplatform.modules.agent.interfaces.dto.UpdateAgentRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 智能体应用服务（application 层）。
 * <p>编排领域聚合与仓储完成智能体的查询、创建、更新、发布、暂停、恢复与删除用例；
 * 业务异常统一转换为 {@link BusinessException}，供接口层通过全局异常处理返回标准错误码。
 * 该服务通过 {@code @NamedInterface("api")} 对其他模块（如门户）开放。</p>
 */
@Service
public class AgentApplicationService {
    private final AgentRepository repository;

    /**
     * @param repository 智能体仓储端口实现
     */
    public AgentApplicationService(AgentRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询智能体列表，支持按状态精确过滤与关键字模糊搜索。
     * <p>当前在内存中过滤（演示规模），关键字匹配名称、编码、负责人与团队，区分大小写；
     * 状态过滤忽略大小写。</p>
     *
     * @param status 状态过滤条件，可为 null/空白表示不过滤
     * @param q      搜索关键字，可为 null/空白表示不过滤
     * @return 命中的智能体视图列表
     */
    public List<AgentSummary> list(String status, String q) {
        return repository.findAll().stream()
                .filter(a -> status == null || status.isBlank() || a.status().name().equalsIgnoreCase(status))
                .filter(a -> q == null || q.isBlank()
                        || a.name().contains(q) || a.code().contains(q) || a.owner().contains(q)
                        || a.team().contains(q))
                .map(AgentSummary::from)
                .toList();
    }

    /**
     * 按 id 查询智能体详情。
     *
     * @param id 智能体唯一标识
     * @return 智能体视图
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public AgentSummary get(String id) {
        return AgentSummary.from(require(id));
    }

    /**
     * 创建草稿状态的智能体。
     * <p>先校验业务编码唯一；id 采用 {@code a_} 前缀加 12 位 UUID 片段。
     * 请求中未提供的模型、负责人、团队、提示词使用演示默认值；
     * 仅当 providerId 非空时才执行模型绑定。</p>
     *
     * @param request 创建请求
     * @return 创建后的智能体视图
     * @throws BusinessException 当编码已存在时（错误码 {@link ErrorCode#NAME_CONFLICT}）
     */
    public AgentSummary create(CreateAgentRequest request) {
        if (repository.findByCode(request.code()).isPresent()) {
            throw new BusinessException(ErrorCode.NAME_CONFLICT, "智能体标识已存在");
        }
        String id = "a_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Agent agent = Agent.draft(id, request.code(), request.name());
        // 未显式提供的展示信息使用演示默认值，保证后台列表可直接渲染
        agent.configure(
                request.model() == null ? "Claude Sonnet 5.5" : request.model(),
                request.owner() == null ? "顾先路" : request.owner(),
                request.team() == null ? "客服平台" : request.team(),
                request.prompt() == null ? "你是企业的智能助手。" : request.prompt()
        );
        if (request.providerId() != null && !request.providerId().isBlank()) {
            agent.bindModel(request.providerId(), request.modelId(), request.model());
        }
        // 模型绑定之后再单独应用工具/知识库，避免被 bindModel 的默认行为覆盖
        agent.update(null, null, null, null, null, request.tools(), request.kb(),
                request.citationKb() == null ? request.kb() : request.citationKb());
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    /**
     * 局部更新智能体配置；未提供或空白的字段保持不变。
     *
     * @param id      智能体唯一标识
     * @param request 更新请求
     * @return 更新后的智能体视图
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public AgentSummary update(String id, UpdateAgentRequest request) {
        Agent agent = require(id);
        agent.update(request.name(), request.model(), request.providerId(), request.modelId(),
                request.owner(), request.team(), request.prompt(), request.tools(), request.kb(),
                request.citationKb());
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    /**
     * 发布智能体（状态置为运行中、发布文案置为全量）。
     *
     * @param id 智能体唯一标识
     * @return 发布后的智能体视图
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public AgentSummary publish(String id) {
        Agent agent = require(id);
        agent.publish();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    /**
     * 暂停智能体（用户门户将不再展示或允许对话）。
     *
     * @param id 智能体唯一标识
     * @return 暂停后的智能体视图
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public AgentSummary pause(String id) {
        Agent agent = require(id);
        agent.pause();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    /**
     * 删除智能体。
     *
     * @param id 智能体唯一标识
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public void delete(String id) {
        require(id);
        repository.deleteById(id);
    }

    /**
     * 恢复已暂停的智能体（状态回到运行中）。
     *
     * @param id 智能体唯一标识
     * @return 恢复后的智能体视图
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    public AgentSummary resume(String id) {
        Agent agent = require(id);
        agent.resume();
        repository.save(agent);
        return AgentSummary.from(agent);
    }

    /**
     * 按 id 加载聚合，不存在时抛出 404 业务异常。
     *
     * @param id 智能体唯一标识
     * @return 智能体聚合
     * @throws BusinessException 当 id 不存在时（错误码 {@link ErrorCode#NOT_FOUND}）
     */
    private Agent require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "智能体不存在: " + id));
    }
}
