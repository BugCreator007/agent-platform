package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch 关键词索引实现（基础设施层）。
 *
 * <p>仅在 {@code app.search.elasticsearch.enabled=true} 时启用；通过 ES REST API 写入文档、
 * 按数据集过滤后做全文 match 查询、按文档条件删除。所有调用异常均记录告警并返回空结果，
 * 由上层检索服务自动回退到数据库内关键词匹配，保证 ES 不可用时服务不中断。</p>
 */
@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchKeywordIndex implements KeywordIndex {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKeywordIndex.class);
    /** 指向 Elasticsearch 的 WebClient。 */
    private final WebClient client;
    /** JSON 解析器。 */
    private final ObjectMapper mapper;
    /** 索引名称。 */
    private final String index;

    /**
     * 构造 ES 关键词索引。
     *
     * @param builder WebClient 构建器
     * @param mapper  JSON 解析器
     * @param url     ES 地址
     * @param index   索引名称
     */
    public ElasticsearchKeywordIndex(WebClient.Builder builder, ObjectMapper mapper,
                                     @Value("${app.search.elasticsearch.url:http://localhost:9200}") String url,
                                     @Value("${app.search.elasticsearch.index:agent_chunks}") String index) {
        this.client = builder.baseUrl(url).build();
        this.mapper = mapper;
        this.index = index;
    }

    /**
     * 以分块 ID 作为 ES 文档 ID 逐条写入（幂等覆盖）。
     *
     * @param records 待索引记录
     */
    @Override
    public void index(List<VectorRecord> records) {
        for (VectorRecord r : records) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("document_id", r.documentId());
            doc.put("dataset_id", r.datasetId());
            doc.put("chunk_index", r.chunkIndex());
            doc.put("content", r.content());
            try {
                // 使用 PUT /{index}/_doc/{id}，重复写入同一分块会覆盖而非产生重复文档
                client.put().uri("/" + index + "/_doc/" + r.id())
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(doc)
                        .retrieve().toBodilessEntity().block(Duration.ofSeconds(10));
            } catch (Exception e) { log.warn("ES index failed: {}", e.getMessage()); }
        }
    }

    /**
     * 按数据集过滤并执行全文检索。
     *
     * @param datasetId 数据集 ID
     * @param query     查询文本
     * @param topK      返回数量上限
     * @return 命中列表；ES 不可用或响应异常时返回空列表
     */
    @Override
    public List<VectorMatch> search(String datasetId, String query, int topK) {
        // bool.filter 限定数据集（不参与打分），must.match 计算内容相关度
        Map<String, Object> body = Map.of(
                "size", Math.max(1, topK),
                "query", Map.of("bool", Map.of(
                        "filter", List.of(Map.of("term", Map.of("dataset_id", datasetId))),
                        "must", List.of(Map.of("match", Map.of("content", query))))));
        try {
            String raw = client.post().uri("/" + index + "/_search")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(15));
            JsonNode hits = raw == null ? null : mapper.readTree(raw).path("hits").path("hits");
            List<VectorMatch> matches = new ArrayList<>();
            if (hits != null && hits.isArray()) {
                for (JsonNode hit : hits) {
                    JsonNode src = hit.path("_source");
                    matches.add(new VectorMatch(hit.path("_id").asText(), src.path("document_id").asText(),
                            src.path("chunk_index").asInt(), src.path("content").asText(),
                            hit.path("_score").asDouble()));
                }
            }
            return matches;
        } catch (Exception e) { log.warn("ES search failed: {}", e.getMessage()); return List.of(); }
    }

    /**
     * 按 {@code document_id} 条件删除该文档的全部索引记录。
     *
     * @param documentId 文档 ID
     */
    @Override
    public void deleteByDocument(String documentId) {
        try {
            client.post().uri("/" + index + "/_delete_by_query")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("query", Map.of("term", Map.of("document_id", documentId))))
                    .retrieve().toBodilessEntity().block(Duration.ofSeconds(10));
        } catch (Exception e) { log.warn("ES delete failed: {}", e.getMessage()); }
    }
}
