package com.agentplatform.modules.workflow.application.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 持久化定时器与父子流程的常驻恢复循环。
 *
 * <p>Always-on recovery loop for persisted timers and parent/child subflows.
 * Unlike the optional cron-like trigger scheduler, this must run in every deployment.</p>
 *
 * <p>所属层：application 应用层。与可选的定时触发调度器不同，本组件不使用
 * {@code @ConditionalOnProperty}，在生产环境始终启用，保证进程重启后等待态实例能够继续推进。
 * 扫描间隔由 {@code app.workflow.resume-interval-ms} 配置，默认 5 秒。</p>
 */
@Component
public class WorkflowResumeScheduler {
    /** 流程执行服务，提供定时器与子流程恢复入口。 */
    private final WorkflowExecutionService execution;

    /**
     * 构造恢复调度器。
     *
     * @param execution 流程执行服务
     */
    public WorkflowResumeScheduler(WorkflowExecutionService execution) {
        this.execution = execution;
    }

    /**
     * 周期扫描并恢复到期定时器与已完成的子流程等待态。
     *
     * <p>使用 fixedDelay 而非 fixedRate：上一轮恢复（可能包含节点执行）结束后再计时，
     * 避免恢复任务堆积。</p>
     */
    @Scheduled(fixedDelayString = "${app.workflow.resume-interval-ms:5000}")
    public void resume() {
        execution.resumeTimers();
        execution.resumeSubflows();
    }
}
