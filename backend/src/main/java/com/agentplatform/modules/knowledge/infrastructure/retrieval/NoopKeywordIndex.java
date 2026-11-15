package com.agentplatform.modules.knowledge.infrastructure.retrieval;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键词索引空实现（基础设施层）。
 *
 * <p>在未启用 Elasticsearch（{@code app.search.elasticsearch.enabled=false}，含未配置）时生效。
 * 写入与删除均为空操作，检索始终返回空列表，促使上层回退到数据库内关键词匹配。</p>
 */
@Component
@ConditionalOnProperty(name = "app.search.elasticsearch.enabled", havingValue = "false", matchIfMissing = true)
public class NoopKeywordIndex implements KeywordIndex {
    /**
     * {@inheritDoc}
     */
    @Override public void index(List<VectorRecord> records) { }
    /**
     * {@inheritDoc}
     *
     * @return 始终为空列表
     */
    @Override public List<VectorMatch> search(String datasetId, String query, int topK) { return List.of(); }
    /**
     * {@inheritDoc}
     */
    @Override public void deleteByDocument(String documentId) { }
}
