---
name: backend-workflow-engine
description: 修改流程编排执行器、图执行、并行/条件/子流程/补偿/定时器、审批恢复或 NOTIFY 发送时使用。
---

# 工作流执行器开发

## 先读

- `modules/workflow/domain/model/{Workflow,WorkflowInstance,WorkflowNodeRun}.java`
- `modules/workflow/application/service/WorkflowExecutionService.java`
- `modules/workflow/application/service/{WorkflowScheduler,WorkflowResumeScheduler,WorkflowNotificationService,WorkflowExpressionService,WorkflowRunState}.java`
- `modules/workflow/interfaces/rest/WorkflowInstanceController.java`
- 前端 `pages/Workflow.tsx`
- `framework/config/AsyncConfig.java`

## 数据模型

```text
workflows              流程定义（nodes_json，节点 config 保存 next/condition/config）
workflow_instances     一次运行实例（state_json、resume_at）
workflow_node_runs     每个节点的执行记录（含 COMPENSATION / SKIPPED）
```

## 执行规则

- `start()` 保存实例后通过 `workflowExecutor` 异步执行。
- 节点 config：`next`（多目标即并行）、`trueNext`/`falseNext`、`delaySeconds`、`workflowId`、`channel`/`target`/`message`、`compensation`。
- 条件节点用 SpEL 计算 `expression`，只激活 true 或 false 出边；未选中的下游节点标记 SKIPPED。
- 同一波次多个 ready 节点并行执行；JOIN 等待所有有效入边，跳过无效分支。
- TIMER 进入 `WAITING_TIMER` 并写 `resume_at`；SUBFLOW 进入 `WAITING_SUBFLOW`；`WorkflowResumeScheduler` 每 5 秒恢复。
- NOTIFY 通过企微 Webhook 或节点 target 的真实 HTTP Webhook 发送；失败会触发节点重试/补偿。
- 节点失败按 `app.workflow.max-attempts` 重试；最终失败时按补偿栈逆序调用工具。
- APPROVAL 暂停实例为 `WAITING_APPROVAL`，审批后从持久化 state 恢复。
- 实例结束更新 `workflows.runs/success`。
- 不要在 HTTP 请求线程中同步跑完整流程。

## 新增节点类型

1. 在 `executeNode` 增加 case。
2. 明确输入/输出 JSON、失败重试语义和副作用幂等性。
3. 若调用外部系统，设置超时并捕获异常转节点失败。
4. 更新前端节点库 `NODE_TYPES` 和节点配置面板。
5. 补审批、失败、重试、取消中的至少一条测试。

## 事务与并发

- 每个节点运行记录单独保存，便于失败恢复。
- 多实例并发时不要依赖内存单例状态。
- 生产需要分布式锁/幂等键，当前只保证单实例顺序执行。
- 定时调度器默认关闭；启用 `WORKFLOW_SCHEDULER_ENABLED=true` 前确认不会重复触发。

## 验证

```bash
curl -X POST http://localhost:8081/api/v1/workflow-instances   -H "Authorization: Bearer <token>"   -H "Content-Type: application/json"   -d '{"workflowId":"wf-refund","input":"订单 202611130097 已签收"}'
```

检查实例 `WAITING_APPROVAL`、节点记录、审批后 `SUCCEEDED`；并跑 `mvn test`。
