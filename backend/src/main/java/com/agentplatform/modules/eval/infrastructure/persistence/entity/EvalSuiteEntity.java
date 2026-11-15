package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.eval.domain.model.EvalSuite;

/**
 * 评测集数据库实体（infrastructure 层），映射 {@code eval_suites} 表。
 */
@TableName(value = "eval_suites", autoResultMap = true)
public class EvalSuiteEntity {
    /** 主键，使用业务侧固定标识（如 ev-cs），不使用数据库自增。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 评测集名称。 */
    private String name;
    /** 覆盖范围/说明。 */
    private String description;
    /** 最近一次通过率（百分比）。 */
    private double rate;
    /** 用例总数。 */
    private int cases;
    /** 最近运行时间展示文本；领域名称为 last，列名 {@code last_run}。 */
    @TableField(value = "last_run") private String lastRun;

    /**
     * 领域对象转数据库实体。
     *
     * @param s 评测集
     * @return 持久化实体
     */
    public static EvalSuiteEntity from(EvalSuite s) { EvalSuiteEntity e = new EvalSuiteEntity(); e.id=s.id(); e.name=s.name(); e.description=s.description(); e.rate=s.rate(); e.cases=s.cases(); e.lastRun=s.last(); return e; }

    /**
     * 数据库实体转领域对象。
     *
     * @return 评测集
     */
    public EvalSuite toDomain() { return new EvalSuite(id, name, description, rate, cases, lastRun); }
}
