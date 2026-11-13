---
name: backend-code-review
description: 对 Agent Platform 后端改动做代码审查时使用；重点检查 DDD 边界、安全、事务、迁移、运行时、RAG、工作流和测试。
---

# 后端代码审查清单

## 先看

```bash
git diff
git status
```

没有 Git 时直接查看改动文件和最近修改时间。

## 模块边界

- [ ] 没有跨模块 import 未暴露的 `domain/infrastructure`。
- [ ] 跨模块 API 放在 `application/query` 或 `application/service`，并加 `@NamedInterface`。
- [ ] domain 不依赖 Spring/JPA/WebClient。
- [ ] `mvn test` 的 Modulith 校验通过。

## 安全

- [ ] 写接口有 `@PreAuthorize`。
- [ ] 公开白名单没有敏感接口。
- [ ] 没有日志泄漏 Token、Cookie、密码、完整提示词。
- [ ] 管理员操作写审计。
- [ ] 用户输入有长度/格式校验。
- [ ] 外网抓取有 SSRF 防护。
- [ ] MCP/HTTP 工具调用有白名单、超时、审批。

## 持久化

- [ ] 新迁移使用下一个版本号，不改旧迁移。
- [ ] `ddl-auto=validate` 下 Entity 与 SQL 一致。
- [ ] delete/批量操作有 `@Transactional`。
- [ ] 没有 N+1 查询或不必要的 findAll 后过滤（数据量大时）。
- [ ] null 字段序列化不会触发 `Map.of` NPE。

## 运行时/RAG

- [ ] SSE 事件顺序 meta → delta → done/error。
- [ ] 新建会话先保存 ChatSession 再保存 SessionMessage。
- [ ] RAG 无结果时明确降级，不编造。
- [ ] 模型/厂商绑定与实际网关一致，或明确提示回退。
- [ ] Embedding 维度与 Milvus 一致。
- [ ] 文档解析失败标记 failed，不删除原文件。

## 工作流

- [ ] 长任务不阻塞 HTTP 线程。
- [ ] 节点失败可重试/可观测。
- [ ] 审批恢复从正确节点继续。
- [ ] 重复触发有幂等/锁策略（生产）。

## 测试

- [ ] 后端 `mvn test`。
- [ ] 前端 `npm run build`。
- [ ] 关键接口 401/403/200。
- [ ] 新增功能有至少一个回归测试。
- [ ] 没有把 `.env`、本地文件、密钥纳入提交。

## 输出格式

按严重级别输出：

```text
[P0] 必须修复：安全/数据损坏/模块边界
[P1] 应该修复：错误处理/事务/性能
[P2] 建议：命名/重复/可观测性
```

每条给出文件、行号、失败场景和修复建议，不要只写风格意见。
