package com.agentplatform.modules.workflow.domain.model;

import java.time.Instant;

/**
 * 流程实例聚合根。
 *
 * <p>所属层：domain 领域层。表示一次流程运行，承载实例状态机与恢复所需的持久化字段：
 * {@code stateJson} 保存执行游标（已完成/已跳过节点、节点输出、失效边、补偿栈），
 * {@code resumeAt} 保存定时器节点的到期时间。</p>
 *
 * <p>状态机：{@code RUNNING → WAITING_APPROVAL | WAITING_TIMER | WAITING_SUBFLOW → RUNNING}
 * （等待态由调度器恢复），终态为 {@code SUCCEEDED}/{@code FAILED}/{@code CANCELLED}。
 * 所有状态迁移都在本类方法内完成，应用层负责持久化。</p>
 */
public class WorkflowInstance {
    /** 实例 ID，形如 {@code wfrun_xxxxxxxxxxxx}。 */
    private final String id;
    /** 所属流程定义 ID。 */
    private final String workflowId;
    /** 流程名称快照，避免流程改名影响历史实例展示。 */
    private final String workflowName;
    /** 实例状态：RUNNING/WAITING_APPROVAL/WAITING_TIMER/WAITING_SUBFLOW/SUCCEEDED/FAILED/CANCELLED。 */
    private String status;
    /** 触发类型，如 manual、scheduled。 */
    private final String triggerType;
    /** 启动输入 JSON，同时作为节点默认输入。 */
    private final String inputJson;
    /** 成功结束时的输出 JSON（包含全部节点输出）。 */
    private String outputJson;
    /** 当前正在执行或等待的节点 key，多个并行节点时以逗号拼接并截断。 */
    private String currentNode;
    /** 启动时间。 */
    private final Instant startedAt;
    /** 结束时间，仅终态有值。 */
    private Instant finishedAt;
    /** 失败原因，仅 FAILED 有值。 */
    private String error;
    /** 执行游标 JSON（{@code WorkflowRunState} 序列化结果），用于崩溃/重启后续跑。 */
    private String stateJson;
    /** 定时器节点的恢复时间，仅 WAITING_TIMER 有值。 */
    private Instant resumeAt;

    /**
     * 兼容旧调用方的构造器：不携带执行游标与恢复时间。
     *
     * @param id           实例 ID
     * @param workflowId   流程定义 ID
     * @param workflowName 流程名称快照
     * @param status       实例状态
     * @param triggerType  触发类型
     * @param inputJson    启动输入 JSON
     * @param outputJson   输出 JSON
     * @param currentNode  当前节点 key
     * @param startedAt    启动时间
     * @param finishedAt   结束时间
     * @param error        失败原因
     */
    public WorkflowInstance(String id, String workflowId, String workflowName, String status,
                            String triggerType, String inputJson, String outputJson, String currentNode,
                            Instant startedAt, Instant finishedAt, String error) {
        this(id, workflowId, workflowName, status, triggerType, inputJson, outputJson, currentNode,
                startedAt, finishedAt, error, null, null);
    }

    /**
     * 完整构造器。
     *
     * @param id           实例 ID
     * @param workflowId   流程定义 ID
     * @param workflowName 流程名称快照
     * @param status       实例状态
     * @param triggerType  触发类型
     * @param inputJson    启动输入 JSON
     * @param outputJson   输出 JSON
     * @param currentNode  当前节点 key
     * @param startedAt    启动时间
     * @param finishedAt   结束时间
     * @param error        失败原因
     * @param stateJson    执行游标 JSON
     * @param resumeAt     定时器恢复时间
     */
    public WorkflowInstance(String id, String workflowId, String workflowName, String status,
                            String triggerType, String inputJson, String outputJson, String currentNode,
                            Instant startedAt, Instant finishedAt, String error,
                            String stateJson, Instant resumeAt) {
        this.id=id; this.workflowId=workflowId; this.workflowName=workflowName; this.status=status;
        this.triggerType=triggerType; this.inputJson=inputJson; this.outputJson=outputJson;
        this.currentNode=currentNode; this.startedAt=startedAt; this.finishedAt=finishedAt; this.error=error;
        this.stateJson=stateJson; this.resumeAt=resumeAt;
    }

    /**
     * 进入待审批等待态；审批节点不占用恢复时间。
     *
     * @param nodeKey 等待中的审批节点 key
     */
    public void waitApproval(String nodeKey) { this.status="WAITING_APPROVAL"; this.currentNode=nodeKey; this.resumeAt=null; }

    /**
     * 进入定时器等待态，记录到期时间供调度扫描。
     *
     * @param nodeKey  等待中的定时器节点 key
     * @param resumeAt 定时器到期时间
     */
    public void waitTimer(String nodeKey, Instant resumeAt) { this.status="WAITING_TIMER"; this.currentNode=nodeKey; this.resumeAt=resumeAt; }

    /**
     * 进入子流程等待态。
     *
     * @param nodeKey 等待中的子流程节点 key
     */
    public void waitSubflow(String nodeKey) { this.status="WAITING_SUBFLOW"; this.currentNode=nodeKey; this.resumeAt=null; }

    /**
     * 回到运行态（等待恢复或继续下一波次节点）。
     *
     * @param nodeKey 当前节点 key
     */
    public void running(String nodeKey) { this.status="RUNNING"; this.currentNode=nodeKey; this.resumeAt=null; }

    /**
     * 保存执行游标 JSON，不改变状态。
     *
     * @param stateJson 序列化后的执行状态
     */
    public void saveState(String stateJson) { this.stateJson=stateJson; }

    /**
     * 标记实例成功并结束。
     *
     * @param output 输出 JSON（通常包含全部节点输出）
     */
    public void succeed(String output) { this.status="SUCCEEDED"; this.outputJson=output; this.finishedAt=Instant.now(); this.resumeAt=null; }

    /**
     * 标记实例失败并记录原因。
     *
     * @param error 失败原因
     */
    public void fail(String error) { this.status="FAILED"; this.error=error; this.finishedAt=Instant.now(); this.resumeAt=null; }

    /** 取消实例并结束（终态，不再恢复）。 */
    public void cancel() { this.status="CANCELLED"; this.finishedAt=Instant.now(); this.resumeAt=null; }

    /** @return 实例 ID */
    public String id() { return id; }
    /** @return 流程定义 ID */
    public String workflowId() { return workflowId; }
    /** @return 流程名称快照 */
    public String workflowName() { return workflowName; }
    /** @return 实例状态 */
    public String status() { return status; }
    /** @return 触发类型 */
    public String triggerType() { return triggerType; }
    /** @return 启动输入 JSON */
    public String inputJson() { return inputJson; }
    /** @return 输出 JSON */
    public String outputJson() { return outputJson; }
    /** @return 当前节点 key */
    public String currentNode() { return currentNode; }
    /** @return 启动时间 */
    public Instant startedAt() { return startedAt; }
    /** @return 结束时间，未结束为 {@code null} */
    public Instant finishedAt() { return finishedAt; }
    /** @return 失败原因，未失败为 {@code null} */
    public String error() { return error; }
    /** @return 执行游标 JSON */
    public String stateJson() { return stateJson; }
    /** @return 定时器恢复时间，非定时等待态为 {@code null} */
    public Instant resumeAt() { return resumeAt; }
}
