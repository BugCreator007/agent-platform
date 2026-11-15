/**
 * 流程编排应用层。
 *
 * <p>编排流程用例：流程定义管理（{@code WorkflowApplicationService}）、流程实例执行与恢复
 * （{@code WorkflowExecutionService}）、条件表达式求值（{@code WorkflowExpressionService}）、
 * 通知发送（{@code WorkflowNotificationService}）以及两个调度器（定时触发与等待态恢复）。</p>
 *
 * <p>约束：本层通过 domain 层仓储端口访问持久化，不直接依赖 infrastructure；
 * 执行过程中的等待态（审批、定时器、子流程）必须写入实例状态，保证进程重启后可恢复。</p>
 */
package com.agentplatform.modules.workflow.application;
