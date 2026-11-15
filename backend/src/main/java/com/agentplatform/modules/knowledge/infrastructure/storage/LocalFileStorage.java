package com.agentplatform.modules.knowledge.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本地文件系统存储实现（基础设施层，默认实现）。
 *
 * <p>在 {@code app.storage.type=local}（含未配置）时启用。文件保存在配置的根目录下，
 * 按数据集分目录，文件名由随机前缀 + 净化后的原始文件名组成，避免冲突与路径穿越。</p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    /** 规范化后的存储根目录绝对路径。 */
    private final Path root;
    /**
     * 构造本地存储。
     *
     * @param root 存储根目录，来自 {@code app.storage.local-root}，默认 {@code ./data/files}
     */
    public LocalFileStorage(@Value("${app.storage.local-root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    /**
     * 保存文件到 {@code <root>/<datasetId>/} 下。
     *
     * @param datasetId   数据集 ID，作为子目录
     * @param filename    原始文件名（会被净化）
     * @param content     文件内容
     * @param contentType MIME 类型（本地存储不记录）
     * @return 存储键 {@code local:<datasetId>/<随机前缀-文件名>}
     * @throws IllegalStateException 创建目录或写文件失败时抛出
     */
    @Override public String store(String datasetId, String filename, byte[] content, String contentType) {
        try {
            Path dir = root.resolve(datasetId);
            Files.createDirectories(dir);
            // 随机前缀避免同名覆盖；sanitize 去除路径分隔符等危险字符
            String name = UUID.randomUUID().toString().replace("-", "") + "-" + sanitize(filename);
            Files.write(dir.resolve(name), content);
            return "local:" + datasetId + "/" + name;
        } catch (Exception e) {
            throw new IllegalStateException("保存文件失败: " + e.getMessage(), e);
        }
    }
    /**
     * 按存储键读取文件；键可带或省略 {@code local:} 前缀。
     *
     * @param storageKey 存储键
     * @return 文件字节
     * @throws IllegalStateException 读取失败时抛出
     */
    @Override public byte[] read(String storageKey) {
        try {
            String relative = storageKey.startsWith("local:") ? storageKey.substring(6) : storageKey;
            // normalize 后仍以 root 为基准解析，降低路径穿越风险
            return Files.readAllBytes(root.resolve(relative).normalize());
        } catch (Exception e) { throw new IllegalStateException("读取文件失败: " + e.getMessage(), e); }
    }
    /**
     * 将文件名中除字母、数字、点、下划线、短横线外的字符替换为下划线。
     *
     * @param name 原始文件名
     * @return 安全的文件名
     */
    private String sanitize(String name) { return name.replaceAll("[^a-zA-Z0-9._-]", "_"); }
}
