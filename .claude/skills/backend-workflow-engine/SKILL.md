---
name: backend-workflow-engine
description: 修改流程编排、图执行、并行/汇聚、条件路由、子流程、补偿、定时器、NOTIFY、审批恢复或工作流测试运行时使用。
---

# 工作流引擎开发

## 先读

- `modules/workflow/domain/model/{Workflow,WorkflowNode,WorkflowInstance,WorkflowNodeRun}.java`
- `modules/workflow/application/service/WorkflowExecutionService.java`
- `WorkflowRunState.java`
- `WorkflowExpressionService.java`
- `WorkflowNotificationService.java`
- `WorkflowScheduler.java`、`WorkflowResumeScheduler.java`
- `modules/workflow/interfaces/rest/{WorkflowController,WorkflowInstanceController}.java`
- `admin-web/src/pages/Workflow.tsx`
- `V8__workflow_runtime_state.sql`

## 领域模型

```text
Workflow(id,name,status,trigger,runs,success,owner,version,updated,nodes)
WorkflowNode(key,type,title,meta,config)
WorkflowInstance(id,workflowId,workflowName,status,triggerType,inputJson,outputJson,currentNode,
                 startedAt,finishedAt,error,stateJson,resumeAt)
WorkflowNodeRun(id,instanceId,nodeKey,nodeType,title,status,inputJson,outputJson,attempt,...)
```

节点类型：

```text
START / TRIGGER
KNOWLEDGE
MODEL
TOOL
CONDITION
APPROVAL
NOTIFY
TIMER
SUBFLOW
JOIN
END
```

## 节点 config

| key | 含义 |
|---|---|
| `next` | 下一步节点数组；多个目标即并行 fan-out |
| `expression` | CONDITION 的 SpEL 表达式 |
| `trueNext` / `falseNext` | 条件真假分支 |
| `delaySeconds` | TIMER 等待秒数 |
| `workflowId` | SUBFLOW 子流程 ID |
| `channel` | NOTIFY：wecom / webhook |
| `target` | webhook URL |
| `message` | 通知模板，支持 `${workflow}`、`${instanceId}`、`${input}`、`${node}` |
| `tool` | TOOL 工具 key |
| `datasetId` | KNOWLEDGE 数据集 ID |
| `compensation` | 失败时补偿工具 key |

## 状态机

```text
RUNNING
  → WAITING_APPROVAL
  → WAITING_TIMER
  → WAITING_SUBFLOW
  → RUNNING
  → SUCCEEDED / FAILED / CANCELLED
```

等待态通过 `stateJson` + `resumeAt` 持久化；`WorkflowResumeScheduler` 每 5 秒恢复。

## 图执行

`WorkflowExecutionService`：

1. 构建 nodes/incoming/outgoing 图。
2. 无显式 `next` 时按画布顺序连接（兼容旧流程）。
3. 计算 ready 节点：所有有效入边已解决，且有至少一条有效入边。
4. 无有效入边 → `SKIPPED`。
5. 同波次 ready 节点用 `workflowExecutor` 并行执行。
6. 条件节点求值后把未选中的边走加入 `inactiveEdges`，下游自动跳过或等待汇聚。
7. JOIN 等待所有有效入边。
8. 全部节点完成/跳过 → SUCCEEDED。
9. 无法推进 → FAILED（环/孤立节点）。

## 条件表达式

- 使用 SpEL + `SimpleEvaluationContext`。
- 上游工具/模型输出会被展开：
  - 嵌套 `output` JSON 解析；
  - `riskScore` 同时提供 `risk_score` 别名；
  - 变量包含 input 字段、`input`、`text`、`nodes`、`outputs`。
- 缺失变量/表达式错误返回 false，不再让整个流程 FAILED。
- 示例：
  - `amount > 500`
  - `risk_score < 80`
  - `address.contains('上海')`
  - `text.contains('上海')`

## TIMER

- 节点执行时返回 WAITING_TIMER，实例写 `resumeAt`。
- `WorkflowResumeScheduler` 到点标记节点成功并恢复。
- `delaySeconds` 最小 1 秒。
- 持久化后可跨进程重启恢复。

## SUBFLOW

- 启动 child workflow 实例。
- 父实例 WAITING_SUBFLOW，state 保存 childId。
- 子流程成功后父节点输出子流程 output；失败则父流程失败并补偿。
- 只阻止直接自引用，跨链环检测未实现。

## 补偿

- 节点成功且配置 `compensation` 时入补偿栈。
- 流程失败时逆序调用补偿工具。
- 生成 `COMPENSATION` 节点运行记录。
- 补偿失败会记录到父实例 error，但不会重试。
- 当前是至少一次语义，缺少幂等键。

## NOTIFY

`WorkflowNotificationService`：

- `channel=wecom`：`app.notify.wecom-webhook`，未配置则失败。
- `channel=webhook`：POST 到 `target`，body 包含 workflow/instance/node/title/message/variables。
- 支持消息模板占位符。
- 发送失败导致节点失败，可触发补偿。

## 审批

- APPROVAL 节点进入 WAITING_APPROVAL。
- `POST /workflow-instances/{id}/approve` 标记节点成功并恢复。
- 当前只有通过/取消，没有拒绝分支、审批人、SLA、转办。

## 测试运行

- `POST /api/v1/workflows/{id}/test` 可传 `{input: "..."}`，启动真实实例。
- 管理端测试弹窗可填写 JSON；编辑器轮询实例状态。
- 测试会真实调用模型、工具、通知，可能产生外部副作用。
- 工作流实例/节点运行记录可在页面查看。

## API

```text
GET    /api/v1/workflows
GET    /api/v1/workflows/{id}
POST   /api/v1/workflows
PUT    /api/v1/workflows/{id}
GET    /api/v1/workflows/{id}/runs
POST   /api/v1/workflows/{id}/test
POST   /api/v1/workflows/{id}/publish

GET    /api/v1/workflow-instances
GET    /api/v1/workflow-instances/{id}
POST   /api/v1/workflow-instances
POST   /api/v1/workflow-instances/{id}/approve
POST   /api/v1/workflow-instances/{id}/cancel
```

## 内置示例

「上海天气查询」`wf-weather`：

```text
START → CONDITION address.contains('上海')
  ├─ true  → TOOL weather.query → JOIN → END
  └─ false → MODEL 兜底回答     → JOIN → END
```

测试输入：`{"address":"上海浦东"}`。

## 排障

- 旧流程测试失败先看 `workflow_instances.error` 和 `workflow_node_runs.error`。
- 条件变量缺失/表达式非法现在应走 false，不应让节点失败；若仍失败，检查是否旧包/旧进程。
- NOTIFY 失败：检查 `app.notify.wecom-webhook` 或节点 target。
- TIMER 不恢复：检查 `WorkflowResumeScheduler`、数据库 `resume_at`、服务进程。
- SUBFLOW 不恢复：检查 child 状态和 `state_json.subflowInstanceId`。
- 并行卡住：检查 JOIN 入边、条件 inactiveEdges、节点目标是否存在。

## 新增节点类型

1. domain 不需要改 node record，只新增 type。
2. `WorkflowExecutionService.executeNode/executeOnce` 增加分支。
3. 前端 `NODE_TYPES`、`TYPE_ALIASES`、配置面板增加字段。
4. 明确持久化、重试、等待、补偿语义。
5. 添加并行/失败/等待至少一条 E2E。
6. 更新 README/skill。
7. 中文注释。

## 当前限制

- 同一并行波次最多一个等待节点。
- RUNNING 实例崩溃后没有自动恢复（等待态可恢复）。
- 流程版本未固定到实例；运行中修改流程会影响后续波次。
- 没有动态分支 Switch、N-of-M JOIN、节点级超时/退避。
- 没有分布式锁/队列，单 JVM 执行。
- 版本历史和回滚仍是简化实现。
- 工作流尚未与智能体绑定为工具。

## Verification

用 API 创建测试流程，覆盖：

- 条件 true/false
- 并行 fan-out + JOIN
- TIMER 恢复
- SUBFLOW
- NOTIFY webhook
- 补偿
- 审批恢复

检查 `workflow_instances.status`、`workflow_node_runs.status/output`、数据库 `state_json`。

## Checklist

- [ ] 节点 config 前后端一致。
- [ ] 状态持久化，可恢复。
- [ ] 条件变量展开与 false 降级。
- [ ] 并行/汇聚无死锁。
- [ ] 补偿逆序执行。
- [ ] NOTIFY 真实发送/失败处理。
- [ ] 测试输入可配置。
- [ ] 中文注释。
