package com.agentplatform.modules.observe.infrastructure.persistence.entity;

import com.agentplatform.modules.observe.domain.model.Alert;
import jakarta.persistence.*;

@Entity
@Table(name = "alerts")
public class AlertEntity {
    @Id private String id;
    private String severity;
    private String title;
    @Column(name = "rule_label") private String ruleLabel;
    private String target;
    @Column(name = "time_label") private String timeLabel;
    private String ago;
    private String status;
    private String metric;
    @Column(columnDefinition = "TEXT") private String detail;
    public static AlertEntity from(Alert a) {
        AlertEntity e = new AlertEntity(); e.id=a.id(); e.severity=a.severity(); e.title=a.title(); e.ruleLabel=a.rule();
        e.target=a.target(); e.timeLabel=a.time(); e.ago=a.ago(); e.status=a.status(); e.metric=a.metric(); e.detail=a.detail(); return e;
    }
    public Alert toDomain() { return new Alert(id, severity, title, ruleLabel, target, timeLabel, ago, status, metric, detail); }
}
