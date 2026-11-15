package com.agentplatform.modules.workflow.application.service;

import com.agentplatform.modules.workflow.domain.model.Workflow;
import com.agentplatform.modules.workflow.domain.model.WorkflowInstance;
import com.agentplatform.modules.workflow.domain.repository.WorkflowInstanceRepository;
import com.agentplatform.modules.workflow.domain.repository.WorkflowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 定时触发器调度器（可选启用）。
 *
 * <p>所属层：application 应用层。周期扫描流程定义，对触发方式包含“定时/每天/每周”
 * 且当前没有活动实例的流程自动发起一次运行。</p>
 *
 * <p>约束：仅当配置 {@code app.workflow.scheduler.enabled=true} 时注册为 Bean；
 * 多实例部署时每台实例都会注册，生产环境应只在一个副本开启，或改造成分布式调度，
 * 否则同一流程可能被重复触发。扫描间隔由 {@code app.workflow.scheduler.interval-ms} 配置，默认 60 秒。</p>
 */
@Component
@ConditionalOnProperty(name = "app.workflow.scheduler.enabled", havingValue = "true")
public class WorkflowScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);
    /** 流程定义仓储端口。 */
    private final WorkflowRepository workflows;
    /** 流程实例仓储端口，用于判断流程是否已有活动实例。 */
    private final WorkflowInstanceRepository instances;
    /** 流程执行服务，用于发起运行。 */
    private final WorkflowExecutionService execution;

    /**
     * 构造定时调度器。
     *
     * @param workflows  流程定义仓储端口
     * @param instances  流程实例仓储端口
     * @param execution  流程执行服务
     */
    public WorkflowScheduler(WorkflowRepository workflows, WorkflowInstanceRepository instances,
                             WorkflowExecutionService execution) {
        this.workflows = workflows; this.instances = instances; this.execution = execution;
    }

    /**
     * 周期检查并触发到期的定时流程。
     *
     * <p>先收集所有活动实例（运行中或等待中）对应的流程 ID，同一流程在上一轮未结束时跳过，
     * 防止定时任务叠加运行。触发方式目前通过中文关键字识别，属于轻量约定。</p>
     */
    @Scheduled(fixedDelayString = "${app.workflow.scheduler.interval-ms:60000}")
    public void tick() {
        // 同一流程存在活动实例时不再触发，避免长流程被每分钟重复启动。
        Set<String> active = Set.copyOf(instances.findAll().stream()
                .filter(i -> Set.of("RUNNING", "WAITING_APPROVAL", "WAITING_TIMER", "WAITING_SUBFLOW").contains(i.status()))
                .map(WorkflowInstance::workflowId).toList());
        for (Workflow workflow : workflows.findAll()) {
            String trigger = workflow.trigger() == null ? "" : workflow.trigger();
            if (trigger.contains("定时") || trigger.contains("每天") || trigger.contains("每周")) {
                if (!active.contains(workflow.id())) {
                    log.info("scheduled workflow start: {}", workflow.name());
                    execution.start(workflow.id(), "{\"trigger\":\"scheduled\"}");
                }
            }
        }
    }
}
