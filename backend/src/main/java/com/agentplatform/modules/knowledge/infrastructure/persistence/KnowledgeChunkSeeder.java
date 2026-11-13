package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.infrastructure.embedding.EmbeddingClient;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.KeywordIndex;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorRecord;
import com.agentplatform.modules.knowledge.infrastructure.retrieval.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Order(20)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class KnowledgeChunkSeeder implements ApplicationRunner {
    private final KnowledgeRepository knowledge;
    private final ChunkRepository chunks;
    private final EmbeddingClient embedding;
    private final VectorStore vectorStore;
    private final KeywordIndex keywordIndex;

    private static final Map<String, List<String>> SAMPLES = Map.of(
            "kb-refund", List.of(
                    "已签收商品在签收后 7 天内、商品完好且不影响二次销售的情况下，可以申请无理由退款。",
                    "已签收订单申请退款时，需先确认商品状态；若已拆封但存在质量问题，可走质量问题退款通道。",
                    "多件订单支持部分退款，退款金额按商品实付金额计算，优惠券分摊部分不予退还。",
                    "审核通过后，退款将在 1 至 3 个工作日内原路退回。"),
            "kb-refund-rules", List.of(
                    "单笔退款超过 500 元时必须由人工复核。",
                    "风险评分低于 60 的账号需人工复核。",
                    "高风险交易应先冻结资金并创建复核单。"),
            "kb-invoice", List.of(
                    "发票抬头填写错误时，可在开具后 30 天内提交红冲申请。",
                    "重开发票需提供原发票号码与正确的抬头信息。",
                    "订单完成后即可申请开票，开票金额以实付金额为准。"),
            "kb-risk", List.of(
                    "风险评分低于 60 的账号需人工复核。",
                    "识别到高风险交易时应立即冻结资金并创建复核单。",
                    "风险规则变更需要经过风控负责人审批。"),
            "kb-ticket", List.of(
                    "工单 88213 的退款争议来自优惠券分摊口径不一致。",
                    "退货运单异常时优先联系承运商并保留物流凭证。")
    );

    public KnowledgeChunkSeeder(KnowledgeRepository knowledge, ChunkRepository chunks,
                                EmbeddingClient embedding, VectorStore vectorStore, KeywordIndex keywordIndex) {
        this.knowledge = knowledge; this.chunks = chunks; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Dataset dataset : knowledge.findAllDatasets()) {
            if (!chunks.findByDataset(dataset.id()).isEmpty()) continue;
            List<Document> docs = knowledge.findDocuments(dataset.id());
            if (docs.isEmpty()) continue;
            List<String> samples = SAMPLES.getOrDefault(dataset.id(), List.of("示例知识片段。"));
            List<DocumentChunk> records = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                Document doc = docs.get(i % docs.size());
                String content = samples.get(i);
                records.add(new DocumentChunk("chunk_seed_" + dataset.id() + "_" + i, doc.id(), dataset.id(),
                        i, content, Math.max(1, content.length() / 2), embedding.embed(content)));
            }
            chunks.saveAll(records);
            List<VectorRecord> vectors = records.stream().map(c -> new VectorRecord(c.id(), c.documentId(),
                    c.datasetId(), c.chunkIndex(), c.content(), c.embedding())).toList();
            vectorStore.upsert(vectors);
            keywordIndex.index(vectors);
        }
    }
}
