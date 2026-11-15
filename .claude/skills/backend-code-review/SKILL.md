---
name: backend-code-review
description: 审查 Agent Platform 后端或全栈改动时使用；重点检查 DDD 边界、MyBatis 持久化、迁移、安全、SSE、RAG、工作流、前端契约和测试。
---

# 代码审查

## 目标

- 先找会造成数据损坏、越权、运行时崩溃、线上迁移失败的问题。
- 再找可维护性、重复实现、边界泄漏。
- 不把风格偏好当阻塞问题。

## 审查输入

```bash
git status
git diff
git diff --stat
```

需要时读：

- 目标模块 `package-info.java`
- 领域模型/仓储端口
- Entity/Mapper/XML/迁移
- Controller/DTO
- 前端 `api.ts`/页面
- 相关测试

## 严重级别

| 级别 | 含义 |
|---|---|
| P0 | 数据丢失、越权、密钥泄露、启动/迁移失败、生产不可用 |
| P1 | 功能错误、边界破坏、事务/并发错误、SSE 泄漏、前端无法使用 |
| P2 | 可维护性、重复、命名、注释、测试缺口 |

## 审查清单

### DDD / Modulith

- [ ] 新模块有 `package-info.java` 和四层目录。
- [ ] 跨模块只依赖 `application/query` / `application/service` NamedInterface。
- [ ] domain 不依赖 Spring/MyBatis/HTTP。
- [ ] application 不直接注入 Mapper/Entity。
- [ ] Controller 不写业务规则/SQL/事务。
- [ ] 没有循环依赖；`ModularityTests` 通过。

### 领域模型

- [ ] 状态迁移在 domain 方法内，Controller 没直接 set status。
- [ ] 新增字段保留兼容构造器。
- [ ] 集合返回不可变副本。
- [ ] null 语义明确。
- [ ] 领域方法有中文注释。

### 持久化 / 迁移

- [ ] 新表/字段有 Flyway，版本唯一。
- [ ] 未修改已应用迁移。
- [ ] Entity `@TableName(autoResultMap=true)`、`@TableField` 与迁移一致。
- [ ] 列表/JSON 字段有 TypeHandler。
- [ ] Mapper XML 使用 `#{}`。
- [ ] 多表写事务正确。
- [ ] H2/MySQL 兼容。
- [ ] 种子数据不覆盖用户数据。
- [ ] 删除级联/路由清理正确。

### API

- [ ] 返回 `ApiResponse`。
- [ ] DTO 校验、错误码、404/409 正确。
- [ ] 写接口 `@PreAuthorize`。
- [ ] 返回 DTO 不泄露密钥/密文/敏感字段。
- [ ] SSE 超时、dispose、错误事件。
- [ ] API 与 `admin-web/user-web api.ts` 同步。

### 安全

- [ ] JWT/角色边界正确。
- [ ] 密钥加密、掩码、无日志。
- [ ] 审计覆盖写操作。
- [ ] CORS 不过宽。
- [ ] 不提交 `.env`/真实 Key/Cookie。
- [ ] 用户门户只暴露 running/grayscale 智能体。

### Agent / 模型

- [ ] SSE 事件契约完整。
- [ ] 模型失败/无 Key 降级明确。
- [ ] fallback 只在未输出 token 时切换。
- [ ] 历史截断、会话落库、trace 更新。
- [ ] MCP 工具循环轮次/错误处理。
- [ ] 不把 provider key 写日志。
- [ ] token 估算不被当成真实计费。

### 知识库

- [ ] 上传/网页来源、解析、分块、向量/关键词索引同步。
- [ ] 替换/删除不残留旧数据。
- [ ] 无 Milvus/ES/Embedding 时能降级。
- [ ] SSRF/Cookie/文件路径安全。
- [ ] rerank 配置是否真的生效。

### 工作流

- [ ] 节点 config 持久化/前后端一致。
- [ ] 条件变量展开、缺失变量 false。
- [ ] 并行/JOIN 就绪判定无死锁。
- [ ] TIMER/SUBFLOW/APPROVAL 状态持久化与恢复。
- [ ] 补偿逆序、失败记录。
- [ ] NOTIFY 真实发送/未配置失败。
- [ ] 工作流版本固定/演练限制有说明。

### 前端

- [ ] `api.ts` 类型与后端一致。
- [ ] loading/error/empty 态。
- [ ] 权限按钮与后端一致。
- [ ] 深色/浅色主题下的硬编码颜色。
- [ ] SSE 中止/错误/增量。
- [ ] 移动端无溢出。
- [ ] `npm run build` 通过。

### 测试

- [ ] `mvn test`。
- [ ] admin/user `npm run build`。
- [ ] 新仓储字段上下文测试。
- [ ] 401/403/200。
- [ ] 运行时/工作流/SSE 手工 E2E。
- [ ] 测试数据清理不影响用户数据。

## 常见项目缺陷

- 两个同版本 Flyway 迁移。
- `Map.of` 传 null。
- 忘记 `autoResultMap` 导致列表读回 null。
- 聊天 SSE 没有 dispose。
- 前端隐藏按钮但后端缺权限。
- 返回 Provider 明文密钥/加密密文。
- 条件表达式缺失变量直接让流程失败。
- 工作流 NOTIFY 默认企微未配置导致测试失败。
- `DB_URL` 含 `&` 未加引号导致 shell/source 失败。
- 本地 MySQL 未启动就启动后端。
- 测试删除用户创建的智能体/流程。

## 报告格式

先列 P0，再 P1，再 P2。每条说明：

```text
文件:行
问题
触发条件/影响
修复建议
```

没有问题时说明已覆盖的检查范围，不要只写“看起来没问题”。
