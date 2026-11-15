package com.agentplatform.modules.system.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.system.domain.model.ApiKey;

@TableName(value = "api_keys", autoResultMap = true)
public class ApiKeyEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    @TableField(value = "key_value") private String keyValue;
    private String scope;
    private String created;
    @TableField(value = "last_used") private String lastUsed;
    @TableField(value = "created_by") private String createdBy;
    public static ApiKeyEntity from(ApiKey k) { ApiKeyEntity e = new ApiKeyEntity(); e.id=k.key(); e.name=k.name(); e.keyValue=k.key(); e.scope=k.scope(); e.created=k.created(); e.lastUsed=k.last(); e.createdBy=k.by(); return e; }
    public ApiKey toDomain() { return new ApiKey(name, keyValue, scope, created, lastUsed, createdBy); }
}
