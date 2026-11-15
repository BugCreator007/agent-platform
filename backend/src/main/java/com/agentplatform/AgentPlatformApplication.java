package com.agentplatform;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Agent 平台启动入口（Spring Boot 应用）。
 *
 * <p>组件扫描范围覆盖 {@code com.agentplatform}，同时通过 {@link MapperScan} 只注册
 * 继承了 MyBatis-Plus {@link BaseMapper} 的持久化接口，避免把领域仓储端口误注册为 Mapper。
 * 开启定时任务与异步执行，用于工作流定时器、告警评估和异步索引。</p>
 */
@EnableScheduling
@EnableAsync
@SpringBootApplication(scanBasePackages = "com.agentplatform")
@MapperScan(basePackages = "com.agentplatform.modules", markerInterface = BaseMapper.class)
public class AgentPlatformApplication {

    /**
     * 启动 Spring 容器。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentPlatformApplication.class, args);
    }
}
