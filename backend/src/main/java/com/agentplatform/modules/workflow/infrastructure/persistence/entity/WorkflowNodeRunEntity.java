package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import com.agentplatform.modules.workflow.domain.model.WorkflowNodeRun;

import java.time.Instant;

/**
 * 节点运行记录持久化实体，对应表 {@code workflow_node_runs}。
 *
 * <p>所属层：infrastructure 基础设施层。每次节点执行（含补偿动作）一行，
 * 与领域模型 {@link WorkflowNodeRun} 通过静态方法互转；执行状态回填与等待态恢复
 * 都依赖本表记录。</p>
 *
 * <p>约束：主键由应用生成（{@code IdType.INPUT}）；等待态记录允许 {@code finished_at} 为空。</p>
 */
@TableName(value = "workflow_node_runs", autoResultMap = true)
public class WorkflowNodeRunEntity {
    /** 运行记录 ID（主键，应用生成）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 所属流程实例 ID。 */
    @TableField(value = "instance_id") private String instanceId;
    /** 节点 key。 */
    @TableField(value = "node_key") private String nodeKey;
    /** 节点类型（补偿记录为 COMPENSATION）。 */
    @TableField(value = "node_type") private String nodeType;
    /** 节点展示名称。 */
    private String title;
    /** 运行状态。 */
    private String status;
    /** 执行输入 JSON。 */
    @TableField(value = "input_json") private String inputJson;
    /** 执行输出 JSON。 */
    @TableField(value = "output_json") private String outputJson;
    /** 尝试次数。 */
    private int attempt;
    /** 开始时间。 */
    @TableField(value = "started_at") private Instant startedAt;
    /** 结束时间，等待态为空。 */
    @TableField(value = "finished_at") private Instant finishedAt;
    /** 失败原因。 */
    private String error;

    /**
     * 领域模型 → 持久化实体。
     *
     * @param r 节点运行记录
     * @return 持久化实体
     */
    public static WorkflowNodeRunEntity from(WorkflowNodeRun r) {
        WorkflowNodeRunEntity e = new WorkflowNodeRunEntity();
        e.id=r.id(); e.instanceId=r.instanceId(); e.nodeKey=r.nodeKey(); e.nodeType=r.nodeType(); e.title=r.title();
        e.status=r.status(); e.inputJson=r.inputJson(); e.outputJson=r.outputJson(); e.attempt=r.attempt();
        e.startedAt=r.startedAt(); e.finishedAt=r.finishedAt(); e.error=r.error(); return e;
    }

    /**
     * 持久化实体 → 领域模型。
     *
     * @return 节点运行记录
     */
    public WorkflowNodeRun toDomain() { return new WorkflowNodeRun(id, instanceId, nodeKey, nodeType, title,
            status, inputJson, outputJson, attempt, startedAt, finishedAt, error); }
}
