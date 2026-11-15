---
name: backend-feature-delivery
description: 端到端交付 Agent Platform 功能时使用；从需求定位到 DDD 分层、MyBatis-Plus 持久化、REST/SSE 接口、安全审计、管理端/用户端契约、测试和部署，避免只改一层。
---

# 功能端到端交付

## 何时使用

- 用户要求“实现一个功能”“加一个页面/接口/表”“把某能力接进来”。
- 需要同时改后端、数据库和 `admin-web` / `user-web`。
- 需要判断改动属于哪个限界上下文、是否需要迁移和权限。
- 不适合：纯文档修改、纯排障、单行样式调整。

## 先建立上下文

1. 读根目录 `CLAUDE.md`、`README.md`、`docs/implementation-status.md`。
2. 读目标模块的 `package-info.java` 和同目录现有实现；不要凭空新建重复能力。
3. 确认当前工程结构：
   - `backend`：Java 21 / Spring Boot 3.5 / Spring Modulith / MyBatis-Plus。
   - `admin-web`：管理后台，React 19 + TypeScript + Vite，端口 5173。
   - `user-web`：用户门户，React 19 + TypeScript + Vite，端口 5174。
   - MySQL 8 + Flyway；测试 H2 MySQL 模式。
4. 需要时加载对应 skill：
   - 新模块/分层：`backend-ddd-module`
   - 领域规则：`backend-domain-model`
   - 表/字段/仓储：`backend-persistence`
   - 接口：`backend-api-endpoint`
   - 权限/密钥/审计：`backend-security`
   - 运行时：`backend-agent-runtime`
   - 多模型：`backend-model-routing`
   - 工具：`backend-tool-execution`
   - MCP：`backend-mcp-integration`
   - RAG：`backend-knowledge-rag`
   - 工作流：`backend-workflow-engine`
   - 指标告警：`backend-observability`
   - 测试：`backend-testing`
   - 部署：`backend-release-deploy`
   - 前台：`frontend-admin-web` / `frontend-user-web`
   - 本地启动：`local-development`

## 功能拆解模板

在写代码前，用一页纸确认：

```text
需求：
所属模块：
涉及聚合：
新增/变更表：
新增/变更接口：
权限角色：
审计要求：
前端页面：
外部依赖：
失败与降级：
验收标准：
```

如果需求跨多个模块，先确定“拥有数据的模块”和“只读消费的模块”，避免把业务规则塞到 Controller 或前端。

## 标准实施顺序

### 1. 契约先行

- 先确定请求/响应 DTO、错误码和前端调用路径。
- 跨模块只依赖对方 `application/query` 或 `application/service` 的 `@NamedInterface` 契约。
- 不在 domain/infrastructure 之间跨模块引用。

### 2. 数据库

- 新增 `backend/src/main/resources/db/migration/V{n}__描述.sql`。
- 已应用的迁移禁止修改；先检查当前最大版本，当前基线到 `V9`。
- 表、索引、字段与 Entity/TypeHandler 同步；测试必须跑 Flyway。
- 详见 `backend-persistence`。

### 3. Domain

- 聚合根负责状态流转和业务不变量。
- 新增字段/构造器保留兼容重载，避免破坏种子数据和测试。
- 仓储端口只放业务方法，不放 MyBatis 细节。
- 详见 `backend-domain-model`。

### 4. Infrastructure

- `persistence/entity`：MyBatis-Plus Entity。
- `persistence/mapper`：`BaseMapper` + XML 自定义 SQL。
- `persistence/repository`：实体仓储；`MybatisXRepository` 实现 domain 端口并聚合多表。
- 多表写操作在应用服务加 `@Transactional`。
- 配置 `@TableName(autoResultMap = true)` 才能让列表/JSON TypeHandler 生效。

### 5. Application

- 用例服务负责编排：校验业务规则、调用仓储、跨模块查询、事务、降级。
- 写操作统一抛 `BusinessException(ErrorCode, message)`。
- 不把 HTTP 细节带进 application；不把 SQL 写进 Controller。

### 6. Interfaces

- Controller 返回 `ApiResponse<T>`。
- `@Valid` 校验 DTO；异常交给 `GlobalExceptionHandler`。
- 写接口加 `@PreAuthorize`；敏感操作只给 `PLATFORM_ADMIN`。
- SSE 接口用 `SseEmitter` + `produces = TEXT_EVENT_STREAM_VALUE`。
- 详见 `backend-api-endpoint`。

### 7. 安全与审计

- 认证由 JWT Resource Server 统一处理；方法级权限用 `@PreAuthorize`。
- 写操作由 `AuditLogFilter` 自动记录，不记录密码、Token、Cookie、完整提示词。
- 厂商/MCP 密钥必须 `SecretCipher` 加密；返回只给掩码。
- 详见 `backend-security`。

### 8. 前端契约

- 后端字段变化同步 `admin-web/src/api.ts` 和/或 `user-web/src/api.ts`。
- 管理端页面遵循 `useQuery/useMutation` + `refresh()` + `toast`。
- 用户端聊天只能走 `/api/v1/user/*`，不要暴露管理接口。
- 权限按钮要与后端角色一致，不能只靠隐藏按钮。
- 详见 `frontend-admin-web`、`frontend-user-web`。

### 9. 种子与迁移兼容

- 演示数据用 `*DataSeeder`，只在首次启动或明确的增量缺失时插入。
- 不覆盖用户已修改的数据。
- 新表为空时插入；有关联顺序时使用 `@Order`。

### 10. 测试与构建

```bash
cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 21) && mvn test
cd admin-web && npm run build
cd user-web && npm run build
```

最低要求：

- 领域规则有单元测试或明确的可复现验证。
- 新仓储字段能被上下文测试读写。
- 新接口验证 401 / 403 / 200。
- 工作流/SSE/RAG 等运行时功能用真实或 mock 服务跑一次 E2E。

### 11. 文档

- 更新 `README.md`、`docs/implementation-status.md`。
- 说明迁移版本、配置项、回滚方式。
- 不把未验证或未生产化的能力写成“已完成”。

## 完成定义

- [ ] 数据落库，重启后仍存在。
- [ ] 表结构由 Flyway 管理，未手改数据库作为唯一实现。
- [ ] Domain 不依赖 Spring/MyBatis/HTTP。
- [ ] 跨模块只访问 NamedInterface。
- [ ] 接口有认证、授权、统一错误包。
- [ ] 写操作进入审计日志，敏感字段不落日志。
- [ ] 前端契约同步，管理端和用户端按角色/状态过滤。
- [ ] `mvn test`、两个前端 `npm run build` 通过。
- [ ] 没有 `.env`、真实 Key、Cookie、用户隐私被提交。
- [ ] 未完成项写入状态文档，不夸大。

## 中文注释规范

项目要求所有新增代码使用中文注释，至少覆盖：

- 类/接口/枚举：说明所属层、职责、关键约束。
- public 方法：说明用途、参数含义、返回值和异常。
- 关键字段：ID、状态、JSON、时间、密钥、外部地址。
- 复杂逻辑：状态机、降级、事务边界、并发、缓存、幂等。
- 注释解释“为什么/约束”，不要只复述代码。

## 常见失败模式

- 新增了 Entity/Mapper 但忘记 XML 或 `autoResultMap`，列表字段读回为空。
- 先写 Controller，后补 Domain，导致业务规则散落。
- 直接改已应用的 Flyway 文件，线上校验失败。
- `Map.of` 接收 null 导致 NPE。
- 前端 api.ts 改了但页面 type 没改，`tsc` 失败。
- 后端 403 只靠前端隐藏按钮，接口仍可越权调用。
- SSE 在 HTTP 线程同步阻塞，连接超时。
- 密钥写进日志、异常信息或提交记录。
