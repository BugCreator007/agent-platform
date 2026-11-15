package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存中的演示种子数据源（基础设施层）。
 *
 * <p>仅在首次启动时由 {@link KnowledgeDataSeeder} 读取并写入持久层，
 * 不参与运行时业务读写，也不作为仓储实现注入。提供与知识库仓储相似的内存查询方法，
 * 便于种子初始化代码复用同一套遍历逻辑。</p>
 */
public class KnowledgeSeedData {
    /** 数据集种子数据，按 ID 索引。 */
    private final Map<String, Dataset> datasets = new ConcurrentHashMap<>();
    /** 文档种子数据，按 ID 索引。 */
    private final Map<String, Document> documents = new ConcurrentHashMap<>();

    /**
     * 初始化 5 个演示数据集与 12 份演示文档。
     */
    public KnowledgeSeedData() {
        seedDataset(Dataset.restore("kb-refund", "售后政策库", "客服平台", "售后政策、退款规则与执行口径。",
                "bge-m3", "HNSW", "COSINE", "默认解析", "标题层级", 512, 64, "hybrid", 4, 0.8,
                true, false, "ready", 128, 1842, "今天 10:24", List.of("a1", "a6")));
        seedDataset(Dataset.restore("kb-refund-rules", "退款规则库", "交易平台", "退款审批权限与风险阈值。",
                "text-embedding-3-large", "HNSW", "COSINE", "表格增强", "标题层级", 512, 96, "hybrid",
                3, 0.75, true, false, "ready", 76, 964, "昨天 17:10", List.of("a2")));
        seedDataset(Dataset.restore("kb-ticket", "工单历史", "客服平台", "历史工单与处理记录。",
                "bge-m3", "IVF_FLAT", "IP", "默认解析", "按段落", 768, 128, "vector",
                5, 0.7, false, false, "indexing", 2340, 18420, "今天 14:05", List.of("a1", "a6")));
        seedDataset(Dataset.restore("kb-invoice", "发票知识库", "财务中台", "发票开具、红冲与抬头校验。",
                "qwen3-embedding", "HNSW", "COSINE", "OCR 增强", "固定长度", 256, 32, "hybrid",
                3, 0.75, true, false, "ready", 54, 630, "2 天前", List.of("a4")));
        seedDataset(Dataset.restore("kb-risk", "风控案例库", "风控", "风险评分规范与处置 SOP。",
                "bge-m3", "HNSW", "L2", "默认解析", "标题层级", 512, 64, "hybrid",
                4, 0.72, false, false, "ready", 310, 2980, "3 天前", List.of("a5", "a8")));

        seedDoc("doc-refund-1", "kb-refund", "七天无理由退款适用范围.md", 3, 24, "42 KB", "今天 10:24");
        seedDoc("doc-refund-2", "kb-refund", "部分退款规则说明.pdf", 2, 41, "1.2 MB", "今天 09:12");
        seedDoc("doc-refund-3", "kb-refund", "已签收商品退款流程.md", 5, 18, "36 KB", "昨天 16:40");
        seedDoc("doc-refund-4", "kb-refund", "优惠券分摊与退款口径.md", 1, 12, "18 KB", "2 天前");
        seedDoc("doc-rules-1", "kb-refund-rules", "退款审批权限矩阵.md", 2, 16, "28 KB", "昨天 17:10");
        seedDoc("doc-rules-2", "kb-refund-rules", "高风险退款复核 SOP.pdf", 4, 33, "860 KB", "3 天前");
        seedDoc("doc-ticket-1", "kb-ticket", "工单 88213 处理记录.md", 2, 32, "54 KB", "今天 14:05");
        seedDoc("doc-ticket-2", "kb-ticket", "退货运单异常案例.md", 1, 21, "31 KB", "今天 13:10");
        seedDoc("doc-invoice-1", "kb-invoice", "发票开具与红冲指南.md", 2, 19, "33 KB", "2 天前");
        seedDoc("doc-invoice-2", "kb-invoice", "发票常见问题.pdf", 1, 27, "740 KB", "3 天前");
        seedDoc("doc-risk-1", "kb-risk", "风控评分使用规范.md", 2, 22, "39 KB", "3 天前");
        seedDoc("doc-risk-2", "kb-risk", "高风险交易处置 SOP.pdf", 3, 35, "1.1 MB", "4 天前");
    }

    /**
     * 登记一个种子数据集。
     *
     * @param dataset 数据集
     */
    private void seedDataset(Dataset dataset) { datasets.put(dataset.id(), dataset); }
    /**
     * 登记一份种子文档。
     *
     * @param id        文档 ID
     * @param datasetId 所属数据集 ID
     * @param name      文档名称
     * @param version   版本号
     * @param chunks    分块数
     * @param size      展示用大小
     * @param updated   更新时间展示文本
     */
    private void seedDoc(String id, String datasetId, String name, int version, long chunks, String size, String updated) {
        documents.put(id, new Document(id, datasetId, name, version, chunks, size, "ready", updated));
    }

    /**
     * @return 全部种子数据集
     */
    public List<Dataset> findAllDatasets() { return List.copyOf(datasets.values()); }
    /**
     * @param id 数据集 ID
     * @return 数据集，不存在时为空
     */
    public Optional<Dataset> findDataset(String id) { return Optional.ofNullable(datasets.get(id)); }
    /**
     * @param dataset 数据集
     */
    public void saveDataset(Dataset dataset) { datasets.put(dataset.id(), dataset); }
    /**
     * @param datasetId 数据集 ID
     * @return 该数据集下的文档列表
     */
    public List<Document> findDocuments(String datasetId) {
        return documents.values().stream().filter(d -> d.datasetId().equals(datasetId)).toList();
    }
    /**
     * @param id 文档 ID
     * @return 文档，不存在时为空
     */
    public Optional<Document> findDocument(String id) { return Optional.ofNullable(documents.get(id)); }
    /**
     * @param document 文档
     */
    public void saveDocument(Document document) { documents.put(document.id(), document); }
    /**
     * @param id 文档 ID
     */
    public void deleteDocument(String id) { documents.remove(id); }
}
