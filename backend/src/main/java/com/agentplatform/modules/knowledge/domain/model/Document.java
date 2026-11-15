package com.agentplatform.modules.knowledge.domain.model;

/**
 * 文档领域模型（不可变记录）。
 *
 * <p>描述数据集下的一份文档及其元数据。文件类文档通过 {@code storageKey} 指向对象存储中的原始文件，
 * 网页类文档（{@code sourceType=WEB}）记录 {@code sourceUrl}；实际分块内容由 {@link DocumentChunk} 承载。</p>
 *
 * @param id          文档主键，格式 {@code doc_<12位十六进制>}
 * @param datasetId   所属数据集 ID
 * @param name        文档名称（通常为文件名或网页标题）
 * @param version     文档版本号，从 1 开始，替换时递增
 * @param chunks      分块数量
 * @param size        展示用文件大小文本，如“42 KB”“1.2 MB”
 * @param status      处理状态：indexing（摄取中）、ready（可检索）、failed（解析失败）、archived（历史版本）
 * @param updated     更新时间展示文本
 * @param storageKey  文件存储键，形如 {@code local:datasetId/xxx} 或 {@code cos:knowledge/datasetId/xxx}
 * @param contentType 文件 MIME 类型；网页来源为 {@code text/plain; source=web}
 * @param checksum    原始内容 SHA-256 摘要，用于去重/校验
 * @param sourceType  来源类型：FILE（上传文件）或 WEB（网页抓取）
 * @param sourceUrl   网页来源地址，仅 WEB 类型有值
 */
public record Document(
        String id, String datasetId, String name, int version, long chunks,
        String size, String status, String updated,
        String storageKey, String contentType, String checksum,
        String sourceType, String sourceUrl
) {
    /**
     * 兼容构造器：仅含展示元数据，不携带存储信息，来源默认为 FILE。
     *
     * @param id        文档主键
     * @param datasetId 所属数据集 ID
     * @param name      文档名称
     * @param version   版本号
     * @param chunks    分块数量
     * @param size      展示用文件大小
     * @param status    处理状态
     * @param updated   更新时间展示文本
     */
    public Document(String id, String datasetId, String name, int version, long chunks,
                    String size, String status, String updated) {
        this(id, datasetId, name, version, chunks, size, status, updated, null, null, null,
                "FILE", null);
    }

    /**
     * 兼容构造器：携带文件存储信息，但不区分网页来源。
     *
     * @param id          文档主键
     * @param datasetId   所属数据集 ID
     * @param name        文档名称
     * @param version     版本号
     * @param chunks      分块数量
     * @param size        展示用文件大小
     * @param status      处理状态
     * @param updated     更新时间展示文本
     * @param storageKey  文件存储键
     * @param contentType 文件 MIME 类型
     * @param checksum    内容 SHA-256 摘要
     */
    public Document(String id, String datasetId, String name, int version, long chunks,
                    String size, String status, String updated,
                    String storageKey, String contentType, String checksum) {
        this(id, datasetId, name, version, chunks, size, status, updated, storageKey, contentType,
                checksum, "FILE", null);
    }
}
