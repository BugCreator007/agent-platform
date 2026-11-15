package com.agentplatform.modules.knowledge.infrastructure.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 本地哈希向量客户端（基础设施层）。
 *
 * <p>基于字符二元组的特征哈希生成定长向量，无需外部服务与密钥，
 * 用于未配置 Embedding Key 时的演示/降级场景。相同文本产生相同向量，
 * 近似文本的相似度具有一定区分度，但不具备语义理解能力。</p>
 */
@Component
public class HashEmbeddingClient implements EmbeddingClient {
    /** 向量维度，默认 384。 */
    private final int dimension;
    /**
     * 构造哈希向量客户端。
     *
     * @param dimension 向量维度，来自 {@code app.ai.embedding.dimension}
     */
    public HashEmbeddingClient(@Value("${app.ai.embedding.dimension:384}") int dimension) {
        this.dimension = dimension;
    }
    /**
     * {@inheritDoc}
     */
    @Override public int dimension() { return dimension; }
    /**
     * 将文本映射为归一化的哈希向量。
     *
     * <p>流程：小写化并压缩空白 → 逐字符取二元组 → SHA-256 哈希取模定位桶并累加 →
     * 按 L2 范数归一化，使余弦相似度等价于点积。</p>
     *
     * @param text 待向量化文本，null 时返回零向量
     * @return 维度为 {@link #dimension()} 的归一化向量
     */
    @Override
    public List<Double> embed(String text) {
        double[] vec = new double[dimension];
        if (text != null) {
            String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
            // 使用相邻两字符（bigram）作为特征，兼顾中文与英文的局部词形信息
            for (int i = 0; i < normalized.length(); i++) {
                int end = Math.min(normalized.length(), i + 2);
                String gram = normalized.substring(i, end);
                int idx = Math.floorMod(hash(gram), dimension);
                vec[idx] += 1.0;
            }
        }
        double norm = 0;
        for (double v : vec) norm += v * v;
        norm = Math.sqrt(norm);
        List<Double> result = new ArrayList<>(dimension);
        // L2 归一化；零向量保持全零，避免除零
        for (double v : vec) result.add(norm == 0 ? 0.0 : v / norm);
        return result;
    }
    /**
     * 对特征串做稳定哈希；SHA-256 不可用时回退到 {@link String#hashCode()}。
     *
     * @param value 特征串
     * @return 32 位哈希值
     */
    private int hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            // 取摘要前 4 字节拼接为 32 位整数
            return ((digest[0] & 0xff) << 24) | ((digest[1] & 0xff) << 16) | ((digest[2] & 0xff) << 8) | (digest[3] & 0xff);
        } catch (Exception e) { return value.hashCode(); }
    }
}
