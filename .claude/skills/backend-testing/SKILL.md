---
name: backend-testing
description: 为 Agent Platform 增加或运行单元、Spring 上下文、MyBatis、Modulith、接口、SSE、工作流端到端测试，以及前端构建验证时使用。
---

# 测试开发

## 当前测试基线

```text
backend/src/test/java/com/agentplatform/ApplicationContextTests.java
backend/src/test/java/com/agentplatform/ModularityTests.java
backend/src/test/resources/application-test.yml
```

- `ApplicationContextTests`：完整上下文 + H2 + Flyway + 种子 + TypeHandler 回读。
- `ModularityTests`：`ApplicationModules.of(...).verify()`。
- 目前测试数量很少，新功能必须补充。

## 测试环境

`application-test.yml`：

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:agent_platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
  flyway:
    enabled: true
app:
  demo:
    seed: true
```

- 使用 H2 MySQL 模式，Flyway 执行真实迁移。
- 每次测试上下文启动都会应用 V1..Vn；迁移必须兼容 H2。
- 种子数据在测试中会写入；测试不要依赖固定用户数据以外的顺序。

## 命令

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test

cd ../admin-web && npm run build
cd ../user-web && npm run build
```

## 测试层次

### 1. Domain 单元测试

- 状态机合法/非法迁移。
- 价格/额度计算。
- 路由排序、表达式求值。
- 不启动 Spring。

### 2. 上下文 / MyBatis 测试

- `@SpringBootTest @ActiveProfiles("test")`
- 验证 Mapper、TypeHandler、Repository 转换。
- 新增表/字段必须有迁移测试。
- 列表/JSON 字段写入后读回。

### 3. 接口测试

- 使用 `@SpringBootTest(webEnvironment=RANDOM_PORT)` + `TestRestTemplate`/MockMvc。
- 关键断言：401、403、200、400、404、409。
- 登录获取 JWT 后带 `Authorization`。
- SSE 用 `WebClient` 或直接调用 runtime 服务验证事件。

### 4. 运行时 E2E（手工/脚本）

- 启动真实 MySQL + Redis + 后端。
- Agent SSE：`meta/delta/done`，MCP 时 `tool/tool-result`。
- 工作流：条件、并行、JOIN、TIMER、SUBFLOW、NOTIFY、补偿、审批。
- 知识库：上传、分块、检索测试。
- 模型路由：provider test、真实 DeepSeek、mock fallback。

### 5. 前端

- `npm run build` 是类型检查和打包的最低门槛。
- 页面改动至少验证登录、加载、空态、错误态。
- SSE 页面要验证增量、停止、错误。
- 深色/浅色主题都要看。

## 测试数据

- 不要删除用户创建的智能体/流程/数据集，除非明确确认。
- 测试流程/实例可用 `wf-<8hex>` 正则识别并清理。
- 测试数据库使用独立 `agent-mysql-local`，不要动生产/远程。
- 清理时先删 node_runs/messages 等子表，再删主表。

## Modulith

- 新模块必须跑 `ModularityTests`。
- 跨模块依赖只允许 NamedInterface。
- 循环依赖会让测试失败。

## 常见失败

- H2 不支持 MySQL 语法 → 改迁移或 SQL。
- Flyway duplicate version → 两个同版本文件。
- TypeHandler 读回 null → `autoResultMap` 缺失。
- 测试依赖种子顺序 → 先查后断言。
- 接口测试没有 Token → 401，不是业务逻辑失败。
- SSE 测试阻塞在流读取 → 设置超时/取消。
- 前端 build 失败常是 api.ts 与页面类型不同步。

## 新增功能验收清单

- [ ] Domain 规则单元测试。
- [ ] 新表/字段迁移在 H2 通过。
- [ ] 仓储读写/TypeHandler 通过。
- [ ] 接口 401/403/200。
- [ ] 关键 SSE/工作流/RAG 手工 E2E。
- [ ] 两个前端 build。
- [ ] 不提交 `.env`/密钥。
- [ ] 迁移/配置/回滚写入文档。
