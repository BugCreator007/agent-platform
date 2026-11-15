package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.common.persistence.StringListTypeHandler;

import com.agentplatform.modules.model.domain.model.ModelInfo;

import java.util.List;

@TableName(value = "models", autoResultMap = true)
public class ModelEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "provider_id") private String providerId;
    private String name;
    @TableField(value = "context_window") private String context;
    @TableField(value = "in_price") private double inPrice;
    @TableField(value = "out_price") private double outPrice;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> capabilities;
    private long calls;
    private String latency;
    private String status;
    @TableField(value = "route_weight") private int routeWeight;
    @TableField(value = "is_default") private boolean isDefault;
    @TableField(value = "upstream_model") private String upstreamModel;

    public static ModelEntity from(ModelInfo m) {
        ModelEntity e = new ModelEntity();
        e.id = m.id(); e.providerId = m.providerId(); e.name = m.name(); e.context = m.context();
        e.inPrice = m.inPrice(); e.outPrice = m.outPrice(); e.capabilities = m.capabilities();
        e.calls = m.calls(); e.latency = m.latency(); e.status = m.status(); e.routeWeight = m.routeWeight();
        e.isDefault = m.isDefault(); e.upstreamModel = m.upstreamModel();
        return e;
    }
    public ModelInfo toDomain() {
        return new ModelInfo(id, providerId, name, context, inPrice, outPrice,
                capabilities == null ? List.of() : capabilities, calls, latency, status, routeWeight,
                isDefault, upstreamModel);
    }
}
