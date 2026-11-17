package com.agentplatform.modules.knowledge.infrastructure.persistence;

import com.agentplatform.modules.knowledge.domain.model.Dataset;
import com.agentplatform.modules.knowledge.domain.model.Document;
import com.agentplatform.modules.knowledge.domain.model.DocumentChunk;
import com.agentplatform.modules.knowledge.domain.repository.ChunkRepository;
import com.agentplatform.modules.knowledge.domain.repository.KnowledgeRepository;
import com.agentplatform.modules.knowledge.domain.service.EmbeddingClient;
import com.agentplatform.modules.knowledge.domain.service.KeywordIndex;
import com.agentplatform.modules.knowledge.domain.service.VectorRecord;
import com.agentplatform.modules.knowledge.domain.service.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 演示用分块种子数据初始化器（基础设施层）。
 *
 * <p>首次启动时为空数据集生成示例分块，计算 Embedding 并写入数据库、向量库与关键词索引，
 * 使演示环境的检索功能开箱可用。仅在 {@code app.demo.seed=true}（默认）时生效，
 * 且数据集已有分块时跳过，不会覆盖真实数据。</p>
 *
 * <p>执行顺序为 20，需在 {@link KnowledgeDataSeeder}（顺序 10）写入数据集与文档之后运行。</p>
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class KnowledgeChunkSeeder implements ApplicationRunner {
    /** 知识库仓储。 */
    private final KnowledgeRepository knowledge;
    /** 分块仓储。 */
    private final ChunkRepository chunks;
    /** Embedding 客户端。 */
    private final EmbeddingClient embedding;
    /** 向量库。 */
    private final VectorStore vectorStore;
    /** 关键词索引。 */
    private final KeywordIndex keywordIndex;

    /** 按数据集 ID 预置的示例文本；未列出的数据集使用通用示例。 */
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

    /**
     * 构造分块种子初始化器。
     *
     * @param knowledge    知识库仓储
     * @param chunks       分块仓储
     * @param embedding    Embedding 客户端
     * @param vectorStore  向量库
     * @param keywordIndex 关键词索引
     */
    public KnowledgeChunkSeeder(KnowledgeRepository knowledge, ChunkRepository chunks,
                                EmbeddingClient embedding, VectorStore vectorStore, KeywordIndex keywordIndex) {
        this.knowledge = knowledge; this.chunks = chunks; this.embedding = embedding;
        this.vectorStore = vectorStore; this.keywordIndex = keywordIndex;
    }

    /**
     * 应用启动后为空数据集生成示例分块并写入三类存储。
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        for (Dataset dataset : knowledge.findAllDatasets()) {
            // 已有分块说明是真实数据或已初始化过，跳过以避免重复
            if (!chunks.findByDataset(dataset.id()).isEmpty()) continue;
            List<Document> docs = knowledge.findDocuments(dataset.id());
            if (docs.isEmpty()) continue;
            List<String> samples = SAMPLES.getOrDefault(dataset.id(), List.of("示例知识片段。"));
            List<DocumentChunk> records = new ArrayList<>();
            for (int i = 0; i < samples.size(); i++) {
                // 示例分块按取模方式轮流挂到该数据集已有文档上
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
