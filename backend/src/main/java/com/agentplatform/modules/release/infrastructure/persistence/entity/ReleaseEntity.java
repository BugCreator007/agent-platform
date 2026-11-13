package com.agentplatform.modules.release.infrastructure.persistence.entity;

import com.agentplatform.modules.release.domain.model.Release;
import jakarta.persistence.*;

@Entity
@Table(name = "releases")
public class ReleaseEntity {
    @Id private String id;
    @Column(name = "agent_id") private String agentId;
    @Column(name = "agent_name") private String agentName;
    private String version;
    private String stage;
    private int traffic;
    @Column(name = "eval_score") private Double evalScore;
    private String owner;
    @Column(name = "time_label") private String timeLabel;
    @Column(columnDefinition = "TEXT") private String note;

    public static ReleaseEntity from(Release r) {
        ReleaseEntity e = new ReleaseEntity();
        e.id = r.id(); e.agentId = r.agentId(); e.agentName = r.agentName(); e.version = r.version();
        e.stage = r.stage(); e.traffic = r.traffic(); e.evalScore = r.evalScore(); e.owner = r.owner();
        e.timeLabel = r.time(); e.note = r.note();
        return e;
    }
    public Release toDomain() {
        return new Release(id, agentId, agentName, version, stage, traffic, evalScore, owner, timeLabel, note);
    }
}
