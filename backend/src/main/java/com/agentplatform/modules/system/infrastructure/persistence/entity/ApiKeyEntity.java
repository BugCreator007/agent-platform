package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.agentplatform.modules.system.domain.model.ApiKey;
import jakarta.persistence.*;

@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {
    @Id private String id;
    private String name;
    @Column(name = "key_value") private String keyValue;
    private String scope;
    private String created;
    @Column(name = "last_used") private String lastUsed;
    @Column(name = "created_by") private String createdBy;
    public static ApiKeyEntity from(ApiKey k) { ApiKeyEntity e = new ApiKeyEntity(); e.id=k.key(); e.name=k.name(); e.keyValue=k.key(); e.scope=k.scope(); e.created=k.created(); e.lastUsed=k.last(); e.createdBy=k.by(); return e; }
    public ApiKey toDomain() { return new ApiKey(name, keyValue, scope, created, lastUsed, createdBy); }
}
