package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.eval.domain.model.EvalSuite;

@TableName(value = "eval_suites", autoResultMap = true)
public class EvalSuiteEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String name;
    private String description;
    private double rate;
    private int cases;
    @TableField(value = "last_run") private String lastRun;
    public static EvalSuiteEntity from(EvalSuite s) { EvalSuiteEntity e = new EvalSuiteEntity(); e.id=s.id(); e.name=s.name(); e.description=s.description(); e.rate=s.rate(); e.cases=s.cases(); e.lastRun=s.last(); return e; }
    public EvalSuite toDomain() { return new EvalSuite(id, name, description, rate, cases, lastRun); }
}
