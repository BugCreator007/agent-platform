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

@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "true")
public class ElasticsearchKeywordIndex implements KeywordIndex {
    private static final Logger log = LoggerFactory.getLogger(ElasticsearchKeywordIndex.class);
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String index;

    public ElasticsearchKeywordIndex(WebClient.Builder builder, ObjectMapper mapper,
                                     @Value("${app.search.elasticsearch.url:http://localhost:9200}") String url,
                                     @Value("${app.search.elasticsearch.index:agent_chunks}") String index) {
        this.client = builder.baseUrl(url).build();
        this.mapper = mapper;
        this.index = index;
    }

    @Override
    public void index(List<VectorRecord> records) {
        for (VectorRecord r : records) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("document_id", r.documentId());
            doc.put("dataset_id", r.datasetId());
            doc.put("chunk_index", r.chunkIndex());
            doc.put("content", r.content());
            try {
                client.put().uri("/" + index + "/_doc/" + r.id())
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(doc)
                        .retrieve().toBodilessEntity().block(Duration.ofSeconds(10));
            } catch (Exception e) { log.warn("ES index failed: {}", e.getMessage()); }
        }
    }

    @Override
    public List<VectorMatch> search(String datasetId, String query, int topK) {
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
