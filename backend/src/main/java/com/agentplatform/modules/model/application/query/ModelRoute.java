package com.agentplatform.modules.model.application.query;

/**
 * 单条模型路由候选（应用层查询 DTO）。
 *
 * <p>由 {@link com.agentplatform.modules.model.application.query.ModelCatalog} 组装，
 * 供 Agent 运行时按顺序尝试调用：包含厂商连接信息、上游模型名、价格与状态。
 * 注意其中的 {@code apiKey} 为解密后的真实密钥，只允许在进程内传递，
 * 不得序列化返回给前端或写入日志。</p>
 *
 * @param providerId     厂商 ID
 * @param providerName   厂商展示名
 * @param protocol       调用协议：openai（OpenAI 兼容）或 anthropic
 * @param baseUrl        厂商 API 基地址
 * @param apiKey         解密后的 API Key（敏感，不可外泄）
 * @param modelId        平台内部模型 ID
 * @param modelName      模型展示名
 * @param upstreamModel  发送给上游的真实模型名
 * @param providerStatus 厂商状态（healthy/degraded/paused）
 * @param modelStatus    模型状态（active/degraded/paused）
 * @param inPrice        输入价格（每百万 token，与厂商计费口径一致）
 * @param outPrice       输出价格（每百万 token）
 * @param routeWeight    路由权重，越大越优先（quality 策略按此排序）
 * @param latencyMs      延迟（毫秒），无法解析时为 {@link Long#MAX_VALUE} 以便排到最后
 */
public record ModelRoute(
        String providerId,
        String providerName,
        String protocol,
        String baseUrl,
        String apiKey,
        String modelId,
        String modelName,
        String upstreamModel,
        String providerStatus,
        String modelStatus,
        double inPrice,
        double outPrice,
        int routeWeight,
        long latencyMs
) {
    /**
     * 判断该路由当前是否可实际调用。
     *
     * @return 配置了 API Key 且厂商、模型均未处于 paused 状态时返回 true
     */
    public boolean available() {
        return apiKey != null && !apiKey.isBlank()
                && !"paused".equalsIgnoreCase(providerStatus)
                && !"paused".equalsIgnoreCase(modelStatus);
    }

    /**
     * 面向界面的路由标签。
     *
     * @return “厂商名 / 模型名” 形式的展示字符串
     */
    public String label() {
        return providerName + " / " + modelName;
    }
}
