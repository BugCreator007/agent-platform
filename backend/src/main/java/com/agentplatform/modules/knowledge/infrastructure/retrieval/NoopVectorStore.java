package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 向量库空实现（基础设施层）。
 *
 * <p>在未启用 Milvus（{@code app.vector.milvus.enabled=false}，含未配置）时生效。
 * 写入与删除均为空操作，检索始终返回空列表，促使上层回退到数据库内向量计算。</p>
 */
@Component
@ConditionalOnProperty(name = "app.vector.milvus.enabled", havingValue = "false", matchIfMissing = true)
public class NoopVectorStore implements VectorStore {
    /** 构造时打印日志，便于确认当前处于无向量库的降级模式。 */
    public NoopVectorStore() { org.slf4j.LoggerFactory.getLogger(NoopVectorStore.class).info("NoopVectorStore active"); }
    /**
     * {@inheritDoc}
     */
    @Override public void upsert(List<VectorRecord> records) { }
    /**
     * {@inheritDoc}
     *
     * @return 始终为空列表
     */
    @Override public List<VectorMatch> search(String datasetId, List<Double> vector, int topK) { return List.of(); }
    /**
     * {@inheritDoc}
     */
    @Override public void deleteByDocument(String documentId) { }
}
