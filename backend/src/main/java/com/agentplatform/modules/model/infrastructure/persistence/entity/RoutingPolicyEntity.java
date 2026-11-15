package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.model.domain.model.RoutingPolicy;

import java.util.List;

@TableName(value = "routing_policy", autoResultMap = true)
public class RoutingPolicyEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String strategy;
    @TableField(value = "primary_model") private String primaryModel;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> fallback;
    private String region;

    public static RoutingPolicyEntity from(RoutingPolicy p) {
        RoutingPolicyEntity e = new RoutingPolicyEntity();
        e.id = "default"; e.strategy = p.strategy(); e.primaryModel = p.primary(); e.fallback = p.fallback(); e.region = p.region();
        return e;
    }
    public RoutingPolicy toDomain() { return new RoutingPolicy(strategy, primaryModel, fallback == null ? List.of() : fallback, region); }
}
