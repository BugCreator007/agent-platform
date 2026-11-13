package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import jakarta.persistence.*;

@Entity
@Table(name = "eval_runs")
public class EvalRunEntity {
    @Id private String id;
    private String suite;
    private String agent;
    private String version;
    private String passed;
    private double score;
    private String duration;
    @Column(name = "time_label") private String timeLabel;
    private String status;
    public static EvalRunEntity from(EvalRun r) { EvalRunEntity e = new EvalRunEntity(); e.id=r.id(); e.suite=r.suite(); e.agent=r.agent(); e.version=r.version(); e.passed=r.passed(); e.score=r.score(); e.duration=r.duration(); e.timeLabel=r.time(); e.status=r.status(); return e; }
    public EvalRun toDomain() { return new EvalRun(id, suite, agent, version, passed, score, duration, timeLabel, status); }
}
