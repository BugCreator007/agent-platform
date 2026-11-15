package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.agentplatform.modules.workflow.infrastructure.persistence.WorkflowNodeListTypeHandler;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;

import java.util.List;

/**
 * 流程定义持久化实体，对应表 {@code workflows}。
 *
 * <p>所属层：infrastructure 基础设施层。与领域模型 {@link Workflow} 通过
 * {@link #from(Workflow)}/{@link #toDomain()} 转换；节点列表以 JSON 形式存于
 * {@code nodes_json} 列，由 {@link WorkflowNodeListTypeHandler} 负责序列化。</p>
 *
 * <p>约束：主键由应用生成（{@code IdType.INPUT}），不使用数据库自增；表结构由 Flyway 维护。</p>
 */
@TableName(value = "workflows", autoResultMap = true)
public class WorkflowEntity {
    /** 流程 ID（主键，应用生成）。 */
    @TableId(type = IdType.INPUT) private String id;
    /** 流程名称。 */
    private String name;
    /** 发布状态：draft/published/paused。 */
    private String status;
    /** 触发方式描述，对应列 {@code trigger_label}（避免与 SQL 关键字冲突）。 */
    @TableField(value = "trigger_label") private String triggerLabel;
    /** 累计运行次数。 */
    private long runs;
    /** 历史成功率（百分比）。 */
    private double success;
    /** 负责人。 */
    private String owner;
    /** 版本号。 */
    private String version;
    /** 最近更新时间文本。 */
    private String updated;
    /** 节点列表 JSON，对应列 {@code nodes_json}。 */
    @TableField(value = "nodes_json", typeHandler = WorkflowNodeListTypeHandler.class) private List<WorkflowNode> nodes;

    /**
     * 领域模型 → 持久化实体。
     *
     * @param w 流程定义
     * @return 持久化实体
     */
    public static WorkflowEntity from(Workflow w) {
        WorkflowEntity e = new WorkflowEntity();
        e.id=w.id(); e.name=w.name(); e.status=w.status(); e.triggerLabel=w.trigger(); e.runs=w.runs();
        e.success=w.success(); e.owner=w.owner(); e.version=w.version(); e.updated=w.updated(); e.nodes=w.nodes(); return e;
    }

    /**
     * 持久化实体 → 领域模型。
     *
     * @return 流程定义；{@code nodes} 为空时使用空列表
     */
    public Workflow toDomain() {
        return new Workflow(id, name, status, triggerLabel, runs, success, owner, version, updated,
                nodes == null ? List.of() : nodes);
    }
}
