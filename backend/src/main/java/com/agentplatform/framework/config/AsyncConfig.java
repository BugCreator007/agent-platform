package com.agentplatform.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步线程池配置。
 *
 * <p>位于技术框架层，为工作流执行与知识库摄取提供隔离的线程池：
 * 两类任务特性不同（工作流耗时波动大、摄取吞吐要求高），分池可避免相互排队拖慢。
 * 拒绝策略使用默认的 AbortPolicy，队列满时快速失败并暴露给调用方。</p>
 */
@Configuration
public class AsyncConfig {
    /**
     * 工作流执行线程池。
     *
     * <p>核心 2、最大 8、队列 200；线程名以 {@code workflow-} 为前缀便于日志排查。
     * 通过 {@code @Async("workflowExecutor")} 或显式注入使用。</p>
     *
     * @return 已初始化的线程池执行器
     */
    @Bean(name = "workflowExecutor")
    ThreadPoolTaskExecutor workflowExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("workflow-");
        executor.initialize();
        return executor;
    }

    /**
     * 知识库摄取线程池。
     *
     * <p>核心 2、最大 6、队列 100；线程名以 {@code ingestion-} 为前缀，
     * 与工作流池隔离，防止大批文档解析挤占流程执行。</p>
     *
     * @return 已初始化的线程池执行器
     */
    @Bean(name = "ingestionExecutor")
    Executor ingestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ingestion-");
        executor.initialize();
        return executor;
    }

    /**
     * 产品埋点写入线程池。
     *
     * <p>核心 1、最大 2、队列 1000；事件写入是 after-commit 的旁路任务，
     * 使用独立小池避免与工作流/摄取争抢资源，队列满时快速失败并由调用方降级。</p>
     *
     * @return 已初始化的线程池执行器
     */
    @Bean(name = "eventExecutor")
    Executor eventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("events-");
        executor.initialize();
        return executor;
    }
}
