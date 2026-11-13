---
name: backend-ddd-module
description: 在 Agent Platform 后端新增或重构一个 DDD 限界上下文时使用；负责模块目录、package-info、四层依赖、Modulith 边界和 JPA 适配器落地。
---

# 后端 DDD 模块开发

## 先读

- `backend/src/main/java/com/agentplatform/modules/agent/`：最小完整模块样板。
- `backend/src/main/java/com/agentplatform/modules/knowledge/`：包含解析、检索、存储等复杂基础设施的样板。
- `backend/src/test/java/com/agentplatform/ModularityTests.java`：模块边界校验入口。
- `backend/src/main/resources/db/migration/`：Flyway 迁移。

## 分层约定

```text
modules/<module>/
├── package-info.java                 # @ApplicationModule
├── interfaces/
│   ├── dto/                          # 请求/响应 DTO
│   └── rest/<X>Controller.java
├── application/
│   ├── service/<X>ApplicationService.java
│   └── query/<X>Summary.java
├── domain/
│   ├── model/<Aggregate>.java
│   └── repository/<X>Repository.java
└── infrastructure/
    ├── persistence/
    │   ├── <X>JpaRepository.java
    │   ├── Jpa<X>Repository.java
    │   ├── entity/<X>Entity.java
    │   └── <X>SeedData.java
    ├── ai/ web/ storage/ ...
```

依赖方向：

```text
interfaces → application → domain
infrastructure → domain（实现端口）
```

禁止：

- `domain` 依赖 Spring、JPA、WebClient、Redis。
- `interfaces` 依赖 `infrastructure`。
- 跨模块直接 import 其他模块的 `domain` 或 `infrastructure`。
- 把业务逻辑写进 Controller 或 JPA Entity。

## 新增模块步骤

1. 建模块根包和 `package-info.java`：

```java
@org.springframework.modulith.ApplicationModule(displayName = "模块中文名")
package com.agentplatform.modules.<module>;
```

2. 先定义 domain 聚合与 repository 端口，不引入框架注解。
3. 在 infrastructure 建 JPA Entity、Spring Data 接口和适配器：

```java
@Primary
@Repository
public class JpaXRepository implements XRepository {
    private final XJpaRepository jpa;
    ...
}
```

4. 增加 Flyway 迁移，见 `backend-persistence` skill。
5. 增加 application service、DTO、Controller。
6. 只有跨模块需要复用时才加 `@NamedInterface`：

```java
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.<module>.application.service;
```

暴露面优先是 `application/query`、`application/service`，不要把 domain 实体暴露给其他模块。

7. 如果需要演示数据，只允许 `*SeedData` 提供初始值，不能恢复内存仓储作为运行时存储。
8. 运行：

```bash
cd backend
mvn test
```

Modulith 会验证模块依赖；出现 `non-exposed type` 时，优先调整依赖方向，不要随意把 infrastructure 全量暴露。

## 命名

- 模块名：小写单数或业务词，如 `knowledge`、`workflow`。
- 聚合：`Agent`、`Dataset`、`WorkflowInstance`。
- 查询模型：`AgentSummary`、`DatasetSummary`。
- 应用服务：`AgentApplicationService`、`WorkflowExecutionService`。
- 仓储端口：`AgentRepository`；JPA 适配器：`JpaAgentRepository`。
