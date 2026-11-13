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
@ConditionalOnProperty(name = "app.vector.milvus.enabled", havingValue = "true")
public class MilvusVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    private final WebClient client;
    private final ObjectMapper mapper;
    private final String collection;
    private final int dimension;
    private final String token;
    private volatile boolean initialized;

    public MilvusVectorStore(WebClient.Builder builder, ObjectMapper mapper,
                             @Value("${app.vector.milvus.url:http://localhost:19530}") String url,
                             @Value("${app.vector.milvus.collection:agent_chunks}") String collection,
                             @Value("${app.vector.milvus.dimension:384}") int dimension,
                             @Value("${app.vector.milvus.token:}") String token) {
        this.client = builder.baseUrl(url).build();
        this.mapper = mapper;
        this.collection = collection;
        this.dimension = dimension;
        this.token = token;
        log.info("MilvusVectorStore enabled, url={}, collection={}", url, collection);
    }

    private void ensureCollection() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            Map<String, Object> idField = Map.of("fieldName", "id", "dataType", "VarChar",
                    "isPrimary", true, "elementTypeParams", Map.of("max_length", "128"));
            Map<String, Object> vectorField = Map.of("fieldName", "embedding", "dataType", "FloatVector",
                    "elementTypeParams", Map.of("dim", String.valueOf(dimension)));
            Map<String, Object> schema = Map.of("autoId", false, "enableDynamicField", true,
                    "fields", List.of(idField, vectorField));
            Map<String, Object> index = Map.of("fieldName", "embedding", "indexName", "embedding",
                    "metricType", "COSINE", "indexType", "AUTOINDEX");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("collectionName", collection);
            body.put("schema", schema);
            body.put("indexParams", List.of(index));
            post("/v2/vectordb/collections/create", body);
            post("/v2/vectordb/collections/load", Map.of("collectionName", collection));
            initialized = true;
        }
    }

    @Override
    public void upsert(List<VectorRecord> records) {
        if (records.isEmpty()) return;
        ensureCollection();
        List<Map<String, Object>> data = new ArrayList<>();
        for (VectorRecord r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.id());
            row.put("embedding", r.embedding());
            row.put("document_id", r.documentId());
            row.put("dataset_id", r.datasetId());
            row.put("chunk_index", r.chunkIndex());
            row.put("content", r.content());
            data.add(row);
        }
        post("/v2/vectordb/entities/insert", Map.of("collectionName", collection, "data", data));
    }

    @Override
    public List<VectorMatch> search(String datasetId, List<Double> vector, int topK) {
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", collection);
        body.put("data", List.of(vector));
        body.put("annsField", "embedding");
        body.put("limit", Math.max(1, topK));
        body.put("filter", "dataset_id == \"" + datasetId.replace("\"", "") + "\"");
        body.put("outputFields", List.of("content", "document_id", "chunk_index"));
        JsonNode response = post("/v2/vectordb/entities/search", body);
        List<VectorMatch> matches = new ArrayList<>();
        if (response == null) return matches;
        JsonNode data = response.path("data");
        // REST returns data as an array of hit-arrays (one per query vector) in most versions.
        JsonNode hits = data.isArray() && data.size() > 0 && data.get(0).isArray() ? data.get(0) : data;
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode entity = hit.has("entity") ? hit.get("entity") : hit;
                matches.add(new VectorMatch(
                        entity.path("id").asText(hit.path("id").asText()),
                        entity.path("document_id").asText(""),
                        entity.path("chunk_index").asInt(0),
                        entity.path("content").asText(""),
                        hit.path("distance").asDouble(hit.path("score").asDouble(0))));
            }
        }
        return matches;
    }

    @Override
    public void deleteByDocument(String documentId) {
        ensureCollection();
        post("/v2/vectordb/entities/delete", Map.of("collectionName", collection,
                "filter", "document_id == \"" + documentId.replace("\"", "") + "\""));
    }

    private JsonNode post(String uri, Object body) {
        try {
            String raw = client.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (!token.isBlank()) h.setBearerAuth(token); })
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));
            JsonNode node = raw == null ? null : mapper.readTree(raw);
            if (node != null && node.path("code").asInt(0) != 0) {
                log.warn("Milvus call {} returned {}: {}", uri, node.path("code").asInt(), node.path("message").asText());
            }
            return node;
        } catch (Exception e) {
            log.warn("Milvus call {} failed: {}", uri, e.getMessage());
            return null;
        }
    }
}
