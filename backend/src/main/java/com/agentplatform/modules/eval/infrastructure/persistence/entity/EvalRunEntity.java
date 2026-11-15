package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.eval.domain.model.EvalRun;

@TableName(value = "eval_runs", autoResultMap = true)
public class EvalRunEntity {
    @TableId(type = IdType.INPUT) private String id;
    private String suite;
    private String agent;
    private String version;
    private String passed;
    private double score;
    private String duration;
    @TableField(value = "time_label") private String timeLabel;
    private String status;
    public static EvalRunEntity from(EvalRun r) { EvalRunEntity e = new EvalRunEntity(); e.id=r.id(); e.suite=r.suite(); e.agent=r.agent(); e.version=r.version(); e.passed=r.passed(); e.score=r.score(); e.duration=r.duration(); e.timeLabel=r.time(); e.status=r.status(); return e; }
    public EvalRun toDomain() { return new EvalRun(id, suite, agent, version, passed, score, duration, timeLabel, status); }
}
