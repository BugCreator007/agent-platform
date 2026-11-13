package com.agentplatform.modules.model.infrastructure.persistence.entity;

import com.agentplatform.common.persistence.StringListConverter;
import com.agentplatform.modules.model.domain.model.RoutingPolicy;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "routing_policy")
public class RoutingPolicyEntity {
    @Id private String id;
    private String strategy;
    @Column(name = "primary_model") private String primaryModel;
    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT") private List<String> fallback;
    private String region;

    public static RoutingPolicyEntity from(RoutingPolicy p) {
        RoutingPolicyEntity e = new RoutingPolicyEntity();
        e.id = "default"; e.strategy = p.strategy(); e.primaryModel = p.primary(); e.fallback = p.fallback(); e.region = p.region();
        return e;
    }
    public RoutingPolicy toDomain() { return new RoutingPolicy(strategy, primaryModel, fallback == null ? List.of() : fallback, region); }
}
