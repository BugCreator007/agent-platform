package com.agentplatform.modules.knowledge.application.query;

/**
 * 单条检索命中结果（知识库对外只读契约）。
 *
 * <p>除原文与分数外，额外携带文档身份与可引用链接，供 Agent 运行时生成
 * “参考资料”并持久化到会话消息。网页文档的 {@code url} 是原始来源地址；
 * 上传文件使用平台受保护的下载接口。</p>
 *
 * @param documentId 文档 ID，可用于去重与下载
 * @param source     来源展示名（通常为文档名称，文档不存在时退化为文档 ID）
 * @param chunk      分块在文档内的顺序下标
 * @param score      相关度分数，向量模式下为余弦相似度，融合模式下为 RRF 分数，均被截断到 ≤ 0.99
 * @param text       命中的分块文本
 * @param url        可引用的文档链接；网页为 sourceUrl，文件为下载接口相对路径
 * @param sourceType 文档来源类型：WEB / FILE
 */
public record RetrievalHit(
        String documentId,
        String source,
        int chunk,
        double score,
        String text,
        String url,
        String sourceType
) {
    /**
     * 兼容旧调用方的构造器：不携带文档链接信息。
     *
     * @param source 来源展示名
     * @param chunk  分块下标
     * @param score  相关度分数
     * @param text   分块文本
     */
    public RetrievalHit(String source, int chunk, double score, String text) {
        this(null, source, chunk, score, text, null, "FILE");
    }
}
