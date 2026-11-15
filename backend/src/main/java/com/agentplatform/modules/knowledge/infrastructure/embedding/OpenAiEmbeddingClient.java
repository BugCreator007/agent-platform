package com.agentplatform.modules.knowledge.infrastructure.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 客户端（基础设施层，主实现）。
 *
 * <p>通过 {@code /embeddings} 接口调用外部向量模型；未配置 API Key 或调用失败时
 * 自动降级到 {@link HashEmbeddingClient}，保证知识库在无密钥环境下仍可运行。
 * 由于标注 {@code @Primary}，注入 {@link EmbeddingClient} 时优先使用本实现。</p>
 */
@Primary
@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {
    /** 降级用的本地哈希向量实现。 */
    private final HashEmbeddingClient fallback;
    /** 指向 Embedding 服务的 WebClient。 */
    private final WebClient webClient;
    /** JSON 解析器。 */
    private final ObjectMapper mapper;
    /** 外部服务 API Key，空串表示未配置。 */
    private final String apiKey;
    /** Embedding 模型名称。 */
    private final String model;
    /** 配置的向量维度（未配置时回退实现的维度）。 */
    private final int dimension;

    /**
     * 构造 OpenAI 兼容客户端。
     *
     * @param fallback   降级哈希向量实现
     * @param builder    WebClient 构建器
     * @param mapper     JSON 解析器
     * @param baseUrl    Embedding 服务基础地址
     * @param apiKey     API Key，可为空
     * @param model      模型名称
     * @param dimension  向量维度
     */
    public OpenAiEmbeddingClient(HashEmbeddingClient fallback, WebClient.Builder builder,
                                 ObjectMapper mapper,
                                 @Value("${app.ai.embedding.base-url:https://api.openai.com/v1}") String baseUrl,
                                 @Value("${app.ai.embedding.api-key:}") String apiKey,
                                 @Value("${app.ai.embedding.model:text-embedding-3-small}") String model,
                                 @Value("${app.ai.embedding.dimension:384}") int dimension) {
        this.fallback = fallback;
        this.webClient = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.dimension = dimension;
    }

    /**
     * 返回实际生效的向量维度：未配置 Key 时与降级实现保持一致。
     */
    @Override public int dimension() { return apiKey.isBlank() ? fallback.dimension() : dimension; }

    /**
     * 调用外部 Embedding 接口生成向量。
     *
     * <p>以下任一情况都会降级到本地哈希向量：未配置 Key、响应缺失/格式异常、网络或超时异常。</p>
     *
     * @param text 待向量化文本
     * @return 向量
     */
    @Override
    public List<Double> embed(String text) {
        // 无密钥时直接使用本地哈希向量，避免无意义的网络调用
        if (apiKey.isBlank()) return fallback.embed(text);
        try {
            JsonNode response = webClient.post().uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(apiKey))
                    .bodyValue(Map.of("model", model, "input", text))
                    .retrieve().bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(30));
            JsonNode embedding = response == null ? null : response.path("data").path(0).path("embedding");
            // 响应格式不符合 OpenAI 约定时降级，避免返回空向量污染索引
            if (embedding == null || !embedding.isArray()) return fallback.embed(text);
            List<Double> vector = new ArrayList<>();
            embedding.forEach(v -> vector.add(v.asDouble()));
            return vector;
        } catch (Exception e) {
            // 外部服务不可用时降级，保证摄取与检索链路不中断
            return fallback.embed(text);
        }
    }
}
