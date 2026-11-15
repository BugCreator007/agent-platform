package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.model.Agent;

/**
 * 模型绑定推断工具（infrastructure 层，包级可见）。
 * <p>根据模型展示名中的关键字推断厂商与厂商模型 id，仅用于演示种子数据和
 * 历史数据的兜底绑定；正常的模型绑定应通过保存智能体配置时显式写入
 * {@code providerId}/{@code modelId}。已绑定 providerId 的聚合不会被修改。</p>
 */
final class AgentModelBinder {
    private AgentModelBinder() {}

    /**
     * 为尚未绑定厂商的智能体按模型名关键字补齐 {@code providerId}/{@code modelId}。
     * <p>匹配不到已知关键字时保持未绑定状态（providerId 仍为 null）。</p>
     *
     * @param agent 待处理的智能体聚合
     */
    static void bind(Agent agent) {
        if (agent.providerId() != null && !agent.providerId().isBlank()) return;
        String model = agent.model() == null ? "" : agent.model();
        // 关键字映射需要与种子/演示数据中的模型命名保持一致
        if (model.contains("Opus")) agent.bindModel("anthropic", "claude-opus-5-5", model);
        else if (model.contains("Haiku")) agent.bindModel("anthropic", "claude-haiku-5-5", model);
        else if (model.contains("Sonnet")) agent.bindModel("anthropic", "claude-sonnet-5-5", model);
        else if (model.contains("Qwen")) agent.bindModel("aliyun", "qwen3-max", model);
        else if (model.contains("DeepSeek")) agent.bindModel("deepseek", "deepseek-v4", model);
        else if (model.contains("GPT")) agent.bindModel("openai", "gpt-5-5", model);
    }
}
