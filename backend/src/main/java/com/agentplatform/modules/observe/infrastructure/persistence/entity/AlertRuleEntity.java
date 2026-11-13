package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.agentplatform.modules.observe.domain.model.AlertRule;
import jakarta.persistence.*;

@Entity
@Table(name = "alert_rules")
public class AlertRuleEntity {
    @Id private String id;
    private String name;
    private String metric;
    private String level;
    @Column(name = "threshold_value") private double thresholdValue;
    @Column(name = "window_label") private String windowLabel;
    private String channel;
    private boolean enabled;
    public static AlertRuleEntity from(AlertRule r) { AlertRuleEntity e = new AlertRuleEntity();
        e.id=r.id(); e.name=r.name(); e.metric=r.metric(); e.level=r.level(); e.thresholdValue=r.thresholdValue();
        e.windowLabel=r.windowLabel(); e.channel=r.channel(); e.enabled=r.enabled(); return e; }
    public AlertRule toDomain() { return new AlertRule(id, name, metric, level, thresholdValue, windowLabel, channel, enabled); }
}
