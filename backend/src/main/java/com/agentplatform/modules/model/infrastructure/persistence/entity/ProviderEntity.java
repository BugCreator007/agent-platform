package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.model.domain.model.Provider;

@TableName(value = "providers", autoResultMap = true)
public class ProviderEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String status;
    private String endpoint;
    private String protocol;
    private long models;
    private long calls;
    private double share;
    private String latency;
    private double success;
    private double quota;
    private double used;
    @TableField(value = "key_value") private String keyValue;
    @TableField(value = "api_key_encrypted") private String apiKeyEncrypted;
    private String region;
    private String route;

    public static ProviderEntity from(Provider p, SecretCipher cipher) {
        ProviderEntity e = new ProviderEntity();
        e.id = p.id(); e.name = p.name(); e.status = p.status(); e.endpoint = p.endpoint();
        e.protocol = p.protocol(); e.models = p.models(); e.calls = p.calls(); e.share = p.share();
        e.latency = p.latency(); e.success = p.success(); e.quota = p.quota(); e.used = p.used();
        e.keyValue = p.key(); e.apiKeyEncrypted = cipher.encrypt(p.apiKey());
        e.region = p.region(); e.route = p.route();
        return e;
    }

    public Provider toDomain(SecretCipher cipher) {
        String apiKey = cipher.decrypt(apiKeyEncrypted);
        return new Provider(id, name, status, endpoint, models, calls, share, latency, success,
                quota, used, keyValue, apiKey, protocol, region, route);
    }

    public String getId() { return id; }
    public String getKeyValue() { return keyValue; }
    public void setKeyValue(String keyValue) { this.keyValue = keyValue; }
}
