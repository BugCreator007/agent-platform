package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class KnowledgeSeedData {
    private final Map<String, Dataset> datasets = new ConcurrentHashMap<>();
    private final Map<String, Document> documents = new ConcurrentHashMap<>();

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

    private void seedDataset(Dataset dataset) { datasets.put(dataset.id(), dataset); }
    private void seedDoc(String id, String datasetId, String name, int version, long chunks, String size, String updated) {
        documents.put(id, new Document(id, datasetId, name, version, chunks, size, "ready", updated));
    }

    public List<Dataset> findAllDatasets() { return List.copyOf(datasets.values()); }
    public Optional<Dataset> findDataset(String id) { return Optional.ofNullable(datasets.get(id)); }
    public void saveDataset(Dataset dataset) { datasets.put(dataset.id(), dataset); }
    public List<Document> findDocuments(String datasetId) {
        return documents.values().stream().filter(d -> d.datasetId().equals(datasetId)).toList();
    }
    public Optional<Document> findDocument(String id) { return Optional.ofNullable(documents.get(id)); }
    public void saveDocument(Document document) { documents.put(document.id(), document); }
    public void deleteDocument(String id) { documents.remove(id); }
}
