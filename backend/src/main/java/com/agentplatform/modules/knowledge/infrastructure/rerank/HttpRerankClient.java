package com.agentplatform.modules.knowledge.infrastructure.rerank;

import com.agentplatform.modules.knowledge.application.query.RerankClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 HTTP 的重排客户端（基础设施层）。
 *
 * <p>默认兼容 Cohere Rerank 风格的请求体：
 * {@code {model, query, documents, top_n}}，响应读取 {@code results[].index/relevance_score}；
 * 同时兼容部分服务把结果放在 {@code output.results} 的结构。</p>
 *
 * <p>配置项：{@code app.rerank.enabled/base-url/api-key/model}。
 * 未启用或未配置地址/Key 时 {@link #configured()} 为 false；
 * 调用失败只记录告警并返回空列表，由检索服务回退到融合排序，不阻断主流程。</p>
 */
@Component
public class HttpRerankClient implements RerankClient {
    private static final Logger log = LoggerFactory.getLogger(HttpRerankClient.class);

    private final WebClient webClient;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    /**
     * 构造重排客户端。
     *
     * @param builder WebClient 构建器
     * @param mapper  JSON 解析器
     * @param enabled 是否启用重排
     * @param baseUrl 重排服务完整地址（含路径，如 https://api.cohere.com/v1/rerank）
     * @param apiKey  鉴权 Key
     * @param model   重排模型名
     */
    public HttpRerankClient(WebClient.Builder builder, ObjectMapper mapper,
                            @Value("${app.rerank.enabled:false}") boolean enabled,
                            @Value("${app.rerank.base-url:}") String baseUrl,
                            @Value("${app.rerank.api-key:}") String apiKey,
                            @Value("${app.rerank.model:}") String model) {
        this.webClient = builder.build();
        this.mapper = mapper;
        this.enabled = enabled;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configured() {
        return enabled && !baseUrl.isBlank() && !apiKey.isBlank();
    }

    /**
     * {@inheritDoc}
     *
     * <p>请求体按 Cohere Rerank 兼容格式构造；非 2xx、超时或解析失败都降级为空结果。</p>
     */
    @Override
    public List<RerankHit> rerank(String query, List<String> documents, int topN) {
        if (!configured() || documents == null || documents.isEmpty()) return List.of();
        Map<String, Object> body = new LinkedHashMap<>();
        if (!model.isBlank()) body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", Math.max(1, topN));
        try {
            String raw = webClient.post().uri(baseUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(15));
            if (raw == null || raw.isBlank()) return List.of();
            JsonNode root = mapper.readTree(raw);
            JsonNode results = root.path("results");
            if (!results.isArray()) results = root.path("output").path("results");
            if (!results.isArray()) return List.of();
            List<RerankHit> hits = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= documents.size()) continue;
                double score = item.path("relevance_score").asDouble(item.path("score").asDouble(0.0));
                hits.add(new RerankHit(index, score));
            }
            return hits;
        } catch (Exception e) {
            log.warn("rerank 调用失败，回退融合排序: {}", e.getMessage());
            return List.of();
        }
    }
}
