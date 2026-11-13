package com.agentplatform.modules.knowledge.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path root;
    public LocalFileStorage(@Value("${app.storage.local-root:./data/files}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    @Override public String store(String datasetId, String filename, byte[] content, String contentType) {
        try {
            Path dir = root.resolve(datasetId);
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + "-" + sanitize(filename);
            Files.write(dir.resolve(name), content);
            return "local:" + datasetId + "/" + name;
        } catch (Exception e) {
            throw new IllegalStateException("保存文件失败: " + e.getMessage(), e);
        }
    }
    @Override public byte[] read(String storageKey) {
        try {
            String relative = storageKey.startsWith("local:") ? storageKey.substring(6) : storageKey;
            return Files.readAllBytes(root.resolve(relative).normalize());
        } catch (Exception e) { throw new IllegalStateException("读取文件失败: " + e.getMessage(), e); }
    }
    private String sanitize(String name) { return name.replaceAll("[^a-zA-Z0-9._-]", "_"); }
}
