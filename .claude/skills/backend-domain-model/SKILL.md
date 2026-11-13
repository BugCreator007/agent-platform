---
name: backend-domain-model
description: 设计或修改 Agent Platform 的聚合、实体、值对象、状态流转和领域仓储端口时使用；确保业务规则在 domain 层而不是 Controller 或 Entity 中。
---

# 领域模型开发

## 先读

- `modules/agent/domain/model/Agent.java`
- `modules/knowledge/domain/model/Dataset.java`
- `modules/workflow/domain/model/WorkflowInstance.java`
- 对应 `domain/repository` 和 `application/service`

## 设计规则

- 聚合负责自己的状态变更，不把状态字段裸露给外部随意 set。
- 状态流转写成领域方法：

```java
public void publish() {
    if (status == AgentStatus.PUBLISHED) throw new IllegalStateException("已发布");
    this.status = AgentStatus.RUNNING;
    this.release = "全量";
    this.updated = "刚刚";
}
```

- 应用服务负责编排、权限和事务；领域对象负责业务不变量。
- 领域层不引用 Spring/JPA/WebClient。
- 值对象优先用 record；有身份和生命周期用 class。
- 仓储端口放在 domain，返回领域对象，不返回 Entity。

## 修改聚合时的兼容性

- 增加字段时保留旧构造器/`restore` 重载，避免 JPA 映射和种子数据同时改动过大。
- 新字段的空值语义要明确：`null`、空字符串还是“未绑定”。
- 修改状态机时补测试：非法状态必须抛 `IllegalStateException` 或 `BusinessException`。

## 领域事件

当前项目没有引入完整事件总线。若新增事件：

- 事件定义放 `domain/event`。
- 发布放在应用服务事务边界内，不要在 domain 里直接依赖 Spring `ApplicationEventPublisher`。
- 需要外部中间件时使用 outbox，不要直接双写数据库和 MQ。

## 验收

- 聚合方法可以独立于 Spring 单元测试。
- 非法状态、重复标识、跨聚合引用都有明确处理。
- `mvn test` 通过，Modulith 边界不新增违规。
