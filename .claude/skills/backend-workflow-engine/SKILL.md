---
name: backend-workflow-engine
description: 修改流程编排执行器、流程实例、节点执行、人工审批恢复、重试或定时触发时使用。
---

# 工作流执行器开发

## 先读

- `modules/workflow/domain/model/{Workflow,WorkflowInstance,WorkflowNodeRun}.java`
- `modules/workflow/application/service/WorkflowExecutionService.java`
- `modules/workflow/application/service/WorkflowScheduler.java`
- `modules/workflow/interfaces/rest/WorkflowInstanceController.java`
- 前端 `pages/Workflow.tsx`
- `framework/config/AsyncConfig.java`

## 数据模型

```text
workflows              流程定义（nodes_json）
workflow_instances     一次运行实例
workflow_node_runs     每个节点的执行记录
```

## 执行规则

- `start()` 保存实例后通过 `workflowExecutor` 异步执行。
- 节点类型：START、KNOWLEDGE、MODEL、TOOL、CONDITION、APPROVAL、NOTIFY、END。
- 节点失败按 `app.workflow.max-attempts` 重试。
- APPROVAL 节点暂停实例为 `WAITING_APPROVAL`，审批后从下一节点恢复。
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
