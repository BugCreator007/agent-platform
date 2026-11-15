package com.agentplatform.modules.eval.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.eval.domain.model.EvalRun;

/**
 * 评测运行记录数据库实体（infrastructure 层），映射 {@code eval_runs} 表。
 */
@TableName(value = "eval_runs", autoResultMap = true)
public class EvalRunEntity {
    /** 主键，由应用层生成（{@code run_} 前缀），不使用数据库自增。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 评测集名称快照。 */
    private String suite;
    /** 被评测智能体名称。 */
    private String agent;
    /** 被评测版本号。 */
    private String version;
    /** 通过用例数展示文本（如 302/320）。 */
    private String passed;
    /** 总得分（百分比）。 */
    private double score;
    /** 耗时展示文本。 */
    private String duration;
    /** 运行时间展示文本；领域名称为 time，列名 {@code time_label}。 */
    @TableField(value = "time_label") private String timeLabel;
    /** 运行状态（success/failed）。 */
    private String status;

    /**
     * 领域记录转数据库实体。
     *
     * @param r 评测运行记录
     * @return 持久化实体
     */
    public static EvalRunEntity from(EvalRun r) { EvalRunEntity e = new EvalRunEntity(); e.id=r.id(); e.suite=r.suite(); e.agent=r.agent(); e.version=r.version(); e.passed=r.passed(); e.score=r.score(); e.duration=r.duration(); e.timeLabel=r.time(); e.status=r.status(); return e; }

    /**
     * 数据库实体转领域记录。
     *
     * @return 评测运行记录
     */
    public EvalRun toDomain() { return new EvalRun(id, suite, agent, version, passed, score, duration, timeLabel, status); }
}
