package com.agentplatform.modules.workflow.infrastructure.persistence.entity;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowNode;
import com.agentplatform.modules.workflow.infrastructure.persistence.WorkflowNodeListConverter;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "workflows")
public class WorkflowEntity {
    @Id private String id;
    private String name;
    private String status;
    @Column(name = "trigger_label") private String triggerLabel;
    private long runs;
    private double success;
    private String owner;
    private String version;
    private String updated;
    @Convert(converter = WorkflowNodeListConverter.class)
    @Column(name = "nodes_json", columnDefinition = "TEXT") private List<WorkflowNode> nodes;

    public static WorkflowEntity from(Workflow w) {
        WorkflowEntity e = new WorkflowEntity();
        e.id=w.id(); e.name=w.name(); e.status=w.status(); e.triggerLabel=w.trigger(); e.runs=w.runs();
        e.success=w.success(); e.owner=w.owner(); e.version=w.version(); e.updated=w.updated(); e.nodes=w.nodes(); return e;
    }
    public Workflow toDomain() {
        return new Workflow(id, name, status, triggerLabel, runs, success, owner, version, updated,
                nodes == null ? List.of() : nodes);
    }
}
