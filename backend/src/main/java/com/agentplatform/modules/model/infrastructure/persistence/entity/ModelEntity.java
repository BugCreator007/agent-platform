package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.agentplatform.common.persistence.StringListConverter;
import com.agentplatform.modules.model.domain.model.ModelInfo;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "models")
public class ModelEntity {
    @Id private String id;
    @Column(name = "provider_id") private String providerId;
    private String name;
    @Column(name = "context_window") private String context;
    @Column(name = "in_price") private double inPrice;
    @Column(name = "out_price") private double outPrice;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT") private List<String> capabilities;
    private long calls;
    private String latency;
    private String status;
    @Column(name = "route_weight") private int routeWeight;
    @Column(name = "is_default") private boolean isDefault;

    public static ModelEntity from(ModelInfo m) {
        ModelEntity e = new ModelEntity();
        e.id = m.id(); e.providerId = m.providerId(); e.name = m.name(); e.context = m.context();
        e.inPrice = m.inPrice(); e.outPrice = m.outPrice(); e.capabilities = m.capabilities();
        e.calls = m.calls(); e.latency = m.latency(); e.status = m.status(); e.routeWeight = m.routeWeight();
        e.isDefault = m.isDefault();
        return e;
    }
    public ModelInfo toDomain() {
        return new ModelInfo(id, providerId, name, context, inPrice, outPrice,
                capabilities == null ? List.of() : capabilities, calls, latency, status, routeWeight, isDefault);
    }
}
