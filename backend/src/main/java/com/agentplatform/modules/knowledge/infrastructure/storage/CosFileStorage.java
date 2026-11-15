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

/**
 * 腾讯云 COS 文件存储实现（基础设施层）。
 *
 * <p>仅在 {@code app.storage.type=cos} 时启用。对象键格式为
 * {@code knowledge/<datasetId>/<随机前缀>-<文件名>}，返回的存储键带 {@code cos:} 前缀，
 * 读取时去除前缀后再访问 COS。</p>
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "cos")
public class CosFileStorage implements FileStorage {
    /** COS 客户端。 */
    private final COSClient client;
    /** 存储桶名称。 */
    private final String bucket;
    /**
     * 构造 COS 存储，使用永久密钥初始化客户端。
     *
     * @param secretId  SecretId
     * @param secretKey SecretKey
     * @param region    地域
     * @param bucket    存储桶名称
     */
    public CosFileStorage(@Value("${app.storage.cos.secret-id}") String secretId,
                          @Value("${app.storage.cos.secret-key}") String secretKey,
                          @Value("${app.storage.cos.region}") String region,
                          @Value("${app.storage.cos.bucket}") String bucket) {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        this.client = new COSClient(credentials, new ClientConfig(new Region(region)));
        this.bucket = bucket;
    }
    /**
     * 上传文件到 COS 并返回带前缀的存储键。
     *
     * @param datasetId   数据集 ID，作为对象键中的目录层级
     * @param filename    原始文件名
     * @param content     文件内容
     * @param contentType MIME 类型，写入对象元数据
     * @return 存储键 {@code cos:knowledge/<datasetId>/<随机前缀-文件名>}
     */
    @Override public String store(String datasetId, String filename, byte[] content, String contentType) {
        String key = "knowledge/" + datasetId + "/" + UUID.randomUUID().toString().replace("-", "") + "-" + filename;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        client.putObject(bucket, key, new ByteArrayInputStream(content), metadata);
        return "cos:" + key;
    }
    /**
     * 按存储键从 COS 读取对象内容；键可带或省略 {@code cos:} 前缀。
     *
     * @param storageKey 存储键
     * @return 文件字节
     * @throws IllegalStateException 读取失败时抛出
     */
    @Override public byte[] read(String storageKey) {
        String key = storageKey.startsWith("cos:") ? storageKey.substring(4) : storageKey;
        try (var in = client.getObject(bucket, key).getObjectContent()) {
            return in.readAllBytes();
        } catch (Exception e) { throw new IllegalStateException("读取 COS 文件失败: " + e.getMessage(), e); }
    }
}
