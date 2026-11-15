package com.agentplatform.modules.agent.application.query;

import com.agentplatform.modules.agent.domain.model.Agent;

import java.util.List;

/**
 * 智能体列表/详情视图（application/query 层，跨模块只读 DTO）。
 * <p>由领域聚合 {@link Agent} 投影而来：状态转为小写字符串，便于前端直接判断与展示；
 * {@code prompt/tools/kb} 为完整配置，门户模块会再裁剪为 {@code UserAgentView}。
 * 该类型通过 {@code @NamedInterface("api")} 对其他模块开放。</p>
 *
 * @param id         智能体唯一标识
 * @param name       展示名称
 * @param code       业务编码
 * @param status     生命周期状态的小写形式（draft/running/grayscale/paused）
 * @param version    版本号
 * @param model      模型展示名称
 * @param providerId 模型厂商标识，未绑定时为 null
 * @param modelId    厂商侧模型标识，未绑定时为 null
 * @param owner      负责人
 * @param team       所属团队
 * @param release    发布状态展示文案
 * @param sessions   累计会话数
 * @param success    成功率百分比
 * @param p95        P95 耗时展示文本
 * @param cost       累计成本
 * @param updated    最近更新时间展示文本
 * @param prompt     系统提示词全文
 * @param tools      工具编码列表
 * @param kb         知识库编码列表
 * @param citationKb 需要返回文档引用的知识库编码列表
 */
public record AgentSummary(
        String id, String name, String code, String status, String version,
        String model, String providerId, String modelId, String owner, String team, String release,
        long sessions, double success, String p95, double cost, String updated,
        String prompt, List<String> tools, List<String> kb, List<String> citationKb
) {
    /**
     * 将领域聚合投影为查询视图。
     * <p>状态取枚举名的小写形式；列表字段直接引用聚合内的不可变列表，无需再拷贝。</p>
     *
     * @param agent 领域聚合，不能为 null
     * @return 对应的只读视图
     */
    public static AgentSummary from(Agent agent) {
        return new AgentSummary(
                agent.id(), agent.name(), agent.code(), agent.status().name().toLowerCase(),
                agent.version(), agent.model(), agent.providerId(), agent.modelId(),
                agent.owner(), agent.team(), agent.release(),
                agent.sessions(), agent.success(), agent.p95(), agent.cost(), agent.updated(),
                agent.prompt(), agent.tools(), agent.kb(), agent.citationKb()
        );
    }
}
