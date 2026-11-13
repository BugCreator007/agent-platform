package com.agentplatform.modules.model.infrastructure.persistence;

import com.agentplatform.modules.model.domain.model.ModelInfo;
import com.agentplatform.modules.model.domain.model.Provider;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ModelSeedData {
    private final Map<String, Provider> providers = new ConcurrentHashMap<>();
    private final Map<String, ModelInfo> models = new ConcurrentHashMap<>();
    private RoutingPolicy routing = new RoutingPolicy(
            "质量优先", "claude-sonnet-5-5",
            List.of("claude-haiku-5-5", "qwen3-max", "deepseek-v4"), "华东 1");

    public ModelSeedData() {
        providers.put("anthropic", new Provider("anthropic", "Anthropic", "healthy",
                "https://api.anthropic.com/v1", 4, 34600, 56.5, "620 ms", 99.98, 12000, 11180,
                "sk-ant-api03-••••••••3f2a", "美国西部 · us-west-2", "主路由"));
        providers.put("aliyun", new Provider("aliyun", "阿里云百炼", "healthy",
                "https://dashscope.aliyuncs.com", 3, 14200, 23.2, "430 ms", 99.96, 5000, 610,
                "sk-dash-••••••••8c71", "华东 2", "备用"));
        providers.put("deepseek", new Provider("deepseek", "DeepSeek", "healthy",
                "https://api.deepseek.com/v1", 2, 6800, 11.1, "510 ms", 99.92, 3000, 420,
                "sk-ds-••••••••2b90", "华东 1", "备用"));
        providers.put("openai", new Provider("openai", "OpenAI", "degraded",
                "https://api.openai.com/v1", 3, 4440, 7.3, "1,860 ms", 98.4, 4000, 200,
                "sk-proj-••••••••c7e4", "美国东部 · us-east-1", "备用"));
        providers.put("zhipu", new Provider("zhipu", "智谱 AI", "paused",
                "https://open.bigmodel.cn/api/paas/v4", 2, 1200, 2.0, "940 ms", 99.2, 1000, 70,
                "zhipu-••••••••91ad", "华北 1", "已停用"));
        model("claude-fable-5-2", "anthropic", "Claude Fable 5.2", "1M", 12, 60,
                List.of("推理", "视觉", "长文本"), 8200, "780 ms", "active", 10, false);
        model("claude-opus-5-5", "anthropic", "Claude Opus 5.5", "200K", 15, 75,
                List.of("推理", "视觉"), 9400, "940 ms", "active", 20, false);
        model("claude-sonnet-5-5", "anthropic", "Claude Sonnet 5.5", "200K", 3, 15,
                List.of("推理", "工具", "视觉"), 12600, "620 ms", "active", 50, true);
        model("claude-haiku-5-5", "anthropic", "Claude Haiku 5.5", "200K", 1, 5,
                List.of("快速", "工具"), 4400, "310 ms", "active", 20, false);
        model("qwen3-max", "aliyun", "Qwen3-Max", "256K", 2.4, 9.6,
                List.of("推理", "中文"), 8200, "430 ms", "active", 15, false);
        model("qwen3-plus", "aliyun", "Qwen3-Plus", "128K", 0.8, 3.2,
                List.of("中文", "快速"), 4100, "290 ms", "active", 10, false);
        model("deepseek-v4", "deepseek", "DeepSeek-V4", "128K", 1.2, 4.8,
                List.of("中文", "代码"), 5200, "510 ms", "active", 10, false);
        model("deepseek-r1", "deepseek", "DeepSeek-R1", "128K", 2, 8,
                List.of("推理", "代码"), 1600, "1,120 ms", "active", 5, false);
        model("gpt-5-5", "openai", "GPT-5.5", "400K", 10, 40,
                List.of("推理", "视觉"), 1900, "1,860 ms", "degraded", 5, false);
        model("gpt-5-5-mini", "openai", "GPT-5.5 mini", "400K", 0.8, 3.2,
                List.of("快速", "视觉"), 2140, "1,240 ms", "active", 5, false);
        model("o4", "openai", "o4", "200K", 20, 80,
                List.of("推理"), 400, "2,410 ms", "active", 2, false);
        model("glm-5", "zhipu", "GLM-5", "128K", 1.5, 6,
                List.of("中文", "工具"), 900, "940 ms", "active", 2, false);
        model("glm-4-6", "zhipu", "GLM-4.6", "128K", 0.6, 2.4,
                List.of("快速", "中文"), 300, "760 ms", "paused", 0, false);
    }

    private void model(String id, String providerId, String name, String context, double inPrice,
                       double outPrice, List<String> caps, long calls, String latency,
                       String status, int routeWeight, boolean isDefault) {
        models.put(id, new ModelInfo(id, providerId, name, context, inPrice, outPrice, caps,
                calls, latency, status, routeWeight, isDefault));
    }

    public List<Provider> findProviders() { return List.copyOf(providers.values()); }
    public Optional<Provider> findProvider(String id) { return Optional.ofNullable(providers.get(id)); }
    public List<ModelInfo> findModels() { return List.copyOf(models.values()); }
    public Optional<ModelInfo> findModel(String id) { return Optional.ofNullable(models.get(id)); }
    public void saveProvider(Provider provider) { providers.put(provider.id(), provider); }
    public void saveModel(ModelInfo model) { models.put(model.id(), model); }
    public RoutingPolicy routing() { return routing; }
    public void saveRouting(RoutingPolicy routing) { this.routing = routing; }
}
