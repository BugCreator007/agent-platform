package com.agentplatform.modules.knowledge.infrastructure.storage;

/**
 * 原始文件存储端口（基础设施层）。
 *
 * <p>实现包括本地文件系统（默认）与腾讯云 COS。存储键带有实现前缀
 * （{@code local:} 或 {@code cos:}），用于后续读取时区分存储后端。</p>
 */
public interface FileStorage {
    /**
     * 保存文件并返回存储键。
     *
     * @param datasetId   所属数据集 ID，用于隔离目录/前缀
     * @param filename    原始文件名
     * @param content     文件内容
     * @param contentType MIME 类型
     * @return 存储键，形如 {@code local:datasetId/xxx} 或 {@code cos:knowledge/datasetId/xxx}
     */
    String store(String datasetId, String filename, byte[] content, String contentType);

    /**
     * 按存储键读取文件内容。
     *
     * @param storageKey 存储键
     * @return 文件字节
     */
    byte[] read(String storageKey);
}
