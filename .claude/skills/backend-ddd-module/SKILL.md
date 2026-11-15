---
name: backend-ddd-module
description: 新增或重构 Agent Platform 的 Spring Modulith 限界上下文、四层目录、NamedInterface、MyBatis 仓储适配器和模块边界时使用。
---

# DDD / Spring Modulith 模块开发

## 何时使用

- 新增业务模块（例如开放平台、计费、租户）。
- 现有模块职责混乱，需要拆分 application/query、domain 或 infrastructure。
- 新增跨模块查询，需要决定暴露哪个 NamedInterface。
- Modulith 测试报告模块依赖越界或循环依赖。

## 当前模块

```text
modules/
  agent     智能体定义、模型绑定、生命周期
  chat      会话运行时、SSE、会话分析读模型
  model     厂商、模型目录、路由、密钥
  tool      工具目录、HTTP/内置工具、MCP
  knowledge 数据集、文档、摄取、检索
  workflow  流程定义、执行、审批、定时器、补偿
  release   发布单、灰度、回滚
  eval      评测集、评测运行、发布门禁
  observe   概览、告警、成本
  system    认证、成员、API Key、审计
  portal    用户门户 API（只读复用 agent/chat）
  open      开放平台占位，尚无实现
```

共享内核在 `common`，技术框架在 `framework`，管理端辅助入口在 `admin`。

## 模块目录模板

```text
modules/<module>/
  package-info.java                 @ApplicationModule(displayName = "...")
  application/
    package-info.java
    query/
      package-info.java             @NamedInterface("api")
      XxxView.java                 只读查询 DTO
    service/
      package-info.java             @NamedInterface("api")
      XxxApplicationService.java
  domain/
    model/                         聚合、值对象、状态枚举
    repository/                    领域仓储端口
  infrastructure/
    persistence/
      entity/XxxEntity.java
      mapper/XxxMapper.java
      repository/XxxRepository.java       实体仓储
      repository/MybatisXxxRepository.java 领域端口适配器
  interfaces/
    dto/                           请求 DTO
    rest/XxxController.java
```

## 暴露规则

- 跨模块只能依赖对方的：
  - `application/query`（读模型、查询服务）
  - `application/service`（用例服务）
- 这两个包必须有 `@NamedInterface("api")`。
- `domain`、`infrastructure`、`interfaces` 不跨模块暴露。
- Controller 只能依赖本模块 application 或其他模块 NamedInterface。
- 不要为了省事让 A 模块直接 import B 模块 Entity/Mapper。

示例：

```java
/**
 * 模型模块对外查询契约。
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.model.application.query;
```

## 依赖方向

```text
interfaces → application → domain
                 ↓
          infrastructure → domain
```

- domain 不依赖 Spring、MyBatis、HTTP、Redis、Jackson。
- infrastructure 实现 domain 端口，可以被 application 通过接口注入。
- application 不直接使用 Mapper/Entity。
- interfaces 不写业务规则和 SQL。

## Repository 分层

每个聚合通常有三层：

1. `domain/repository/XRepository.java`：领域端口。
2. `infrastructure/persistence/repository/XRepository.java`：包装 Mapper，返回 Entity。
3. `infrastructure/persistence/repository/MybatisXRepository.java`：实现领域端口，完成 Entity ↔ Domain 转换；多表时聚合多个实体仓储。

示例职责：

```java
// 实体仓储：只做 MyBatis 读写
@Repository
public class AgentRepository {
    private final AgentMapper mapper;
    public List<AgentEntity> findAll() { return mapper.selectList(null); }
    public void save(AgentEntity entity) { ... }
}
```

```java
// 领域适配器：转换领域对象
@Primary
@Repository
public class MybatisAgentRepository implements com...agent.domain.repository.AgentRepository {
    private final AgentRepository rows;
    ...
}
```

## 跨模块示例

- `chat` 读取模型路由：依赖 `model.application.query.ModelCatalog/ModelRoute`。
- `chat` 读取知识库：依赖 `knowledge.application.service.KnowledgeApplicationService`。
- `observe` 读取会话事实：依赖 `chat.application.query.SessionAnalyticsService`。
- `portal` 读取智能体与运行时：依赖 `agent.application.service.AgentApplicationService`、`chat.application.runtime.AgentRuntimeService`。
- `release` 调用评测门禁：依赖 `eval.application.service.EvalGateService`。

## 新模块 Checklist

- [ ] 新建 `package-info.java` 与 `@ApplicationModule`。
- [ ] 建立四层目录，不放错包。
- [ ] 需要跨模块的包加 `@NamedInterface("api")`。
- [ ] 定义领域聚合与仓储端口。
- [ ] Entity/Mapper/实体仓储/领域适配器齐全。
- [ ] 新表迁移与 TypeHandler 一致。
- [ ] Controller、DTO、权限、错误码。
- [ ] 种子数据可重复启动。
- [ ] `ModularityTests` 通过。
- [ ] 更新 `.claude/skills` 中的模块地图（如果新增职责）。
- [ ] 所有类和关键方法有中文注释。

## 常见错误

- 把 Mapper 注入 application service，越过端口。
- 从 `chat` 直接 import `model.infrastructure.entity.ProviderEntity`。
- 把 `@NamedInterface` 加在 domain 包上。
- 新建模块后忘记 `package-info.java`，Modulith 不识别边界。
- Controller 直接拼 SQL 或调用多个 Mapper。
- 领域对象引入 `@TableName` 等持久化注解。
