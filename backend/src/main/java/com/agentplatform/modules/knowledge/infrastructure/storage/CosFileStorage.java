package com.agentplatform.modules.knowledge.infrastructure.storage;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cos")
public class CosFileStorage implements FileStorage {
    private final COSClient client;
    private final String bucket;
    public CosFileStorage(@Value("${app.storage.cos.secret-id}") String secretId,
                          @Value("${app.storage.cos.secret-key}") String secretKey,
                          @Value("${app.storage.cos.region}") String region,
                          @Value("${app.storage.cos.bucket}") String bucket) {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        this.client = new COSClient(credentials, new ClientConfig(new Region(region)));
        this.bucket = bucket;
    }
    @Override public String store(String datasetId, String filename, byte[] content, String contentType) {
        String key = "knowledge/" + datasetId + "/" + UUID.randomUUID().toString().replace("-", "") + "-" + filename;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        client.putObject(bucket, key, new ByteArrayInputStream(content), metadata);
        return "cos:" + key;
    }
    @Override public byte[] read(String storageKey) {
        String key = storageKey.startsWith("cos:") ? storageKey.substring(4) : storageKey;
        try (var in = client.getObject(bucket, key).getObjectContent()) {
            return in.readAllBytes();
        } catch (Exception e) { throw new IllegalStateException("读取 COS 文件失败: " + e.getMessage(), e); }
    }
}
