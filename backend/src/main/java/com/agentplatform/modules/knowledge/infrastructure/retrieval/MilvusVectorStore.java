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
 * Milvus 向量库实现（基础设施层）。
 *
 * <p>仅在 {@code app.vector.milvus.enabled=true} 时启用；通过 Milvus v2 REST API
 * 完成集合创建/加载、向量写入、按数据集过滤的相似度检索与按文档删除。集合采用
 * 动态字段模式，除主键与向量外的业务字段作为动态列存储。</p>
 *
 * <p>容错策略：所有调用失败只记录告警并返回 null/空结果，由上层检索服务回退到数据库内向量计算。</p>
 */
@Component
@ConditionalOnProperty(name = "app.vector.milvus.enabled", havingValue = "true")
public class MilvusVectorStore implements VectorStore {
    private static final Logger log = LoggerFactory.getLogger(MilvusVectorStore.class);
    /** 指向 Milvus REST 服务的 WebClient。 */
    private final WebClient client;
    /** JSON 解析器。 */
    private final ObjectMapper mapper;
    /** 集合名称。 */
    private final String collection;
    /** 向量维度，用于建集合时的 schema。 */
    private final int dimension;
    /** 访问令牌，可为空。 */
    private final String token;
    /** 集合是否已完成创建/加载的本地标记，避免每次调用都重复初始化。 */
    private volatile boolean initialized;

    /**
     * 构造 Milvus 向量库客户端。
     *
     * @param builder    WebClient 构建器
     * @param mapper     JSON 解析器
     * @param url        Milvus REST 地址
     * @param collection 集合名称
     * @param dimension  向量维度
     * @param token      访问令牌，可为空
     */
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

    /**
     * 惰性初始化集合：首次调用时创建集合与向量索引并加载到内存。
     *
     * <p>使用双重检查锁保证并发下只初始化一次；创建失败不会抛出异常，
     * {@code initialized} 保持 false，后续调用会重试。</p>
     */
    private void ensureCollection() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            // 主键使用业务分块 ID（VarChar），关闭 autoId 以便与数据库/ES 的 ID 对齐
            Map<String, Object> idField = Map.of("fieldName", "id", "dataType", "VarChar",
                    "isPrimary", true, "elementTypeParams", Map.of("max_length", "128"));
            Map<String, Object> vectorField = Map.of("fieldName", "embedding", "dataType", "FloatVector",
                    "elementTypeParams", Map.of("dim", String.valueOf(dimension)));
            // enableDynamicField=true：document_id、dataset_id 等字段无需预先声明即可写入
            Map<String, Object> schema = Map.of("autoId", false, "enableDynamicField", true,
                    "fields", List.of(idField, vectorField));
            Map<String, Object> index = Map.of("fieldName", "embedding", "indexName", "embedding",
                    "metricType", "COSINE", "indexType", "AUTOINDEX");
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("collectionName", collection);
            body.put("schema", schema);
            body.put("indexParams", List.of(index));
            post("/v2/vectordb/collections/create", body);
            // 新集合需 load 后才能检索
            post("/v2/vectordb/collections/load", Map.of("collectionName", collection));
            initialized = true;
        }
    }

    /**
     * 批量写入分块向量及其动态字段。
     *
     * @param records 待写入记录；空列表直接返回
     */
    @Override
    public void upsert(List<VectorRecord> records) {
        if (records.isEmpty()) return;
        ensureCollection();
        List<Map<String, Object>> data = new ArrayList<>();
        for (VectorRecord r : records) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.id());
            row.put("embedding", r.embedding());
            // 以下字段落入动态字段，供检索结果回填与按数据集/文档过滤
            row.put("document_id", r.documentId());
            row.put("dataset_id", r.datasetId());
            row.put("chunk_index", r.chunkIndex());
            row.put("content", r.content());
            data.add(row);
        }
        // 注意：调用的是 insert 接口；相同主键重复写入的行为由 Milvus 实现决定
        post("/v2/vectordb/entities/insert", Map.of("collectionName", collection, "data", data));
    }

    /**
     * 在指定数据集内执行向量检索。
     *
     * @param datasetId 数据集 ID，用于拼接过滤表达式
     * @param vector    查询向量
     * @param topK      返回数量上限
     * @return 命中列表；调用失败时为空列表
     */
    @Override
    public List<VectorMatch> search(String datasetId, List<Double> vector, int topK) {
        ensureCollection();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("collectionName", collection);
        body.put("data", List.of(vector));
        body.put("annsField", "embedding");
        body.put("limit", Math.max(1, topK));
        // 过滤条件使用动态字段 dataset_id；先剔除引号避免表达式注入
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
                        // 新版返回 distance，旧版可能返回 score，二者取其一
                        hit.path("distance").asDouble(hit.path("score").asDouble(0))));
            }
        }
        return matches;
    }

    /**
     * 按动态字段 {@code document_id} 删除该文档的全部向量。
     *
     * @param documentId 文档 ID
     */
    @Override
    public void deleteByDocument(String documentId) {
        ensureCollection();
        // 同样先剔除引号，避免过滤表达式被注入
        post("/v2/vectordb/entities/delete", Map.of("collectionName", collection,
                "filter", "document_id == \"" + documentId.replace("\"", "") + "\""));
    }

    /**
     * 统一的 Milvus REST 调用：附加可选 Bearer Token，解析 JSON 并检查业务错误码。
     *
     * @param uri  相对路径
     * @param body 请求体
     * @return 响应 JSON 节点；网络异常或响应无法解析时返回 null
     */
    private JsonNode post(String uri, Object body) {
        try {
            String raw = client.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (!token.isBlank()) h.setBearerAuth(token); })
                    .bodyValue(body)
                    .retrieve().bodyToMono(String.class)
                    .block(Duration.ofSeconds(20));
            JsonNode node = raw == null ? null : mapper.readTree(raw);
            // Milvus 以 code != 0 表示业务失败，此处只告警不中断，交由上层回退
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
