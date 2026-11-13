package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.agentplatform.modules.eval.domain.model.EvalSuite;
import jakarta.persistence.*;

@Entity
@Table(name = "eval_suites")
public class EvalSuiteEntity {
    @Id private String id;
    private String name;
    @Column(columnDefinition = "TEXT") private String description;
    private double rate;
    private int cases;
    @Column(name = "last_run") private String lastRun;
    public static EvalSuiteEntity from(EvalSuite s) { EvalSuiteEntity e = new EvalSuiteEntity(); e.id=s.id(); e.name=s.name(); e.description=s.description(); e.rate=s.rate(); e.cases=s.cases(); e.lastRun=s.last(); return e; }
    public EvalSuite toDomain() { return new EvalSuite(id, name, description, rate, cases, lastRun); }
}
