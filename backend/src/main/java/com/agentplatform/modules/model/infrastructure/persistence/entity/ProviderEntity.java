package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.model.domain.model.Provider;

/**
 * 厂商表（providers）持久化实体（infrastructure 层）。
 *
 * <p>密钥安全：真实 API Key 只以密文形式保存在 {@code api_key_encrypted} 列，
 * 读写时通过 {@link SecretCipher} 加解密；{@code key_value} 仅存脱敏展示值。
 * 与领域对象 {@link Provider} 通过 {@link #from(Provider, SecretCipher)}、
 * {@link #toDomain(SecretCipher)} 互转。</p>
 */
@TableName(value = "providers", autoResultMap = true)
public class ProviderEntity {
    /** 厂商 ID（业务主键，非自增）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 厂商名称。 */
    private String name;
    /** 状态：healthy/degraded/paused。 */
    private String status;
    /** API 基地址。 */
    private String endpoint;
    /** 调用协议：openai/anthropic。 */
    private String protocol;
    /** 模型数量（统计展示）。 */
    private long models;
    /** 累计调用次数（统计展示）。 */
    private long calls;
    /** 调用占比（统计展示）。 */
    private double share;
    /** 平均延迟展示值。 */
    private String latency;
    /** 成功率。 */
    private double success;
    /** 额度上限。 */
    private double quota;
    /** 已用额度。 */
    private double used;
    /** 脱敏后的密钥展示值。 */
    @TableField(value = "key_value") private String keyValue;
    /** 加密后的真实 API Key 密文。 */
    @TableField(value = "api_key_encrypted") private String apiKeyEncrypted;
    /** 区域。 */
    private String region;
    /** 路由角色展示值。 */
    private String route;

    /**
     * 领域对象转持久化实体；真实密钥在此处加密。
     *
     * @param p      厂商领域对象
     * @param cipher 密钥加解密组件
     * @return 对应实体
     */
    public static ProviderEntity from(Provider p, SecretCipher cipher) {
        ProviderEntity e = new ProviderEntity();
        e.id = p.id(); e.name = p.name(); e.status = p.status(); e.endpoint = p.endpoint();
        e.protocol = p.protocol(); e.models = p.models(); e.calls = p.calls(); e.share = p.share();
        e.latency = p.latency(); e.success = p.success(); e.quota = p.quota(); e.used = p.used();
        e.keyValue = p.key(); e.apiKeyEncrypted = cipher.encrypt(p.apiKey());
        e.region = p.region(); e.route = p.route();
        return e;
    }

    /**
     * 持久化实体转领域对象；密文在此处解密，密文为空时得到空密钥。
     *
     * @param cipher 密钥加解密组件
     * @return 厂商领域对象
     */
    public Provider toDomain(SecretCipher cipher) {
        String apiKey = cipher.decrypt(apiKeyEncrypted);
        return new Provider(id, name, status, endpoint, models, calls, share, latency, success,
                quota, used, keyValue, apiKey, protocol, region, route);
    }

    /**
     * 实体 ID。
     *
     * @return 厂商 ID
     */
    public String getId() { return id; }

    /**
     * 脱敏后的密钥展示值。
     *
     * @return 展示值
     */
    public String getKeyValue() { return keyValue; }

    /**
     * 设置脱敏后的密钥展示值。
     *
     * @param keyValue 展示值
     */
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
}
