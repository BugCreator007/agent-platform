package com.agentplatform.modules.agent.infrastructure.persistence;

import com.agentplatform.modules.agent.domain.model.Agent;

final class AgentModelBinder {
    private AgentModelBinder() {}
    static void bind(Agent agent) {
        if (agent.providerId() != null && !agent.providerId().isBlank()) return;
        String model = agent.model() == null ? "" : agent.model();
        if (model.contains("Opus")) agent.bindModel("anthropic", "claude-opus-5-5", model);
        else if (model.contains("Haiku")) agent.bindModel("anthropic", "claude-haiku-5-5", model);
        else if (model.contains("Sonnet")) agent.bindModel("anthropic", "claude-sonnet-5-5", model);
        else if (model.contains("Qwen")) agent.bindModel("aliyun", "qwen3-max", model);
        else if (model.contains("DeepSeek")) agent.bindModel("deepseek", "deepseek-v4", model);
        else if (model.contains("GPT")) agent.bindModel("openai", "gpt-5-5", model);
    }
}
