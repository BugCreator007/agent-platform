# 迭代 01 · 独立复审结论（KQ-1 + B0）

| 项目 | 内容 |
|---|---|
| 复审人 | 本会话（独立只读复审，未修改任何代码） |
| 复审对象 | [07 实现报告](07-implementation-report.md)、当前工作区（基线 `2abefbf` + 未提交改动） |
| 复审日期 | 2026-11-18 |
| 结论 | **通过**（第三次复验，2026-11-18）：P0/P1 全部闭环；42 个测试、双端构建与 MySQL V11→V13 迁移均复验通过；剩余仅管理端 E2E 与真实 Redis 限流压测 |

## 1. 独立复跑结果

| 验证项 | 结果 |
|---|---|
| `mvn -q -f backend/pom.xml test` | **32 个测试通过，0 失败 0 错误**（surefire 汇总） |
| `npm --prefix user-web run build` | 通过（exit 0） |
| `npm --prefix admin-web run build` | 通过（exit 0） |
| 代码核对 | 逐项核对 07 第 5 节列出的 P0/P1 与 B0 实现 |

## 2. P0 复核：全部闭环

| 编号 | 结论 | 核对依据 |
|---|---|---|
| P0-1 会话读接口属主校验 | ✅ 通过 | 列表/详情/消息统一走 `SessionAccessPolicy`；非本人与历史无主统一 404；`SessionSecurityTest` 覆盖 VIEWER 本人/他人/历史无主、DEVELOPER 可读 |
| P0-2 续聊与拒答归属 | ✅ 通过 | `AgentRuntimeService.stream` 在写入任何消息前校验 `canResume`；拒答、普通回答、MCP 回答三条路径均保留既有 `createdBy`，不再认领历史无主会话；`AgentRuntimeServiceOwnershipTest` 覆盖 |
| P0-3 历史无主会话反馈 | ✅ 通过 | `FeedbackApplicationService.requireOwnedSession` 对空归属直接 404，对他人归属 403；单测覆盖 |
| P0-4 B0 埋点 | ✅ 通过 | V12 `product_events`、9 类事件、after-commit 异步、`event_id` 幂等、失败降级、客户端白名单与限流均已实现；`ProductEventRecorderTest` 覆盖属性过滤、幂等与失败吞掉 |
| P0-5 合入测试门槛 | ✅ 通过（有余量） | 32 个测试覆盖归属、反馈、严格模式、分页、PII、埋点与迁移；双端构建通过 |

补充说明：无参 `stream(request)` 现在按匿名上下文处理，已有会话会被拒绝；生产入口均显式传 `UserContext`，未发现内部调用依赖旧行为。

## 3. 新发现问题

### P1-1 · `/api/v1/sessions/stats` 未加角色限制

- **证据**：`SessionController.java:61` 的 `@GetMapping("/stats")` 没有 `@PreAuthorize`，而 `SecurityConfig` 只要求 `anyRequest().authenticated()`。任何 VIEWER 登录后都可读取全量智能体的会话数、成功率与 P95。
- **影响**：与 01 记录 Q10「管理端会话/反馈/洞察仅 PLATFORM_ADMIN、DEVELOPER 可见」不一致；虽不含对话原文，仍属管理端指标越权。
- **修复**：加 `@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")`，并补一条 VIEWER 403 的接口测试。

### P2-1 · 角色未统一大写，与注释不一致

- **证据**：`CurrentUser.java` 注释声明「去掉 `ROLE_` 前缀并统一大写」，但代码只做了去前缀与去重，没有 `toUpperCase()`；`UserContext` 也未在构造时规范化。
- **影响**：本地 JWT 角色是大写，暂无问题；OIDC 若返回 `viewer` / `developer` 小写，`SessionAccessPolicy.canReadAll` 会判定失败，管理员能力静默丢失。
- **修复**：在 `CurrentUser.roles()` 或 `UserContext` 构造器中统一 `toUpperCase(Locale.ROOT)`。

### P2-2 · 客户端事件的 `messageId` 未校验是否属于该会话

- **证据**：`UserEventController` 只校验了 `sessionId` 的归属，没有校验 `messageId` 是否属于该会话；`answer_copied` / `citation_downloaded` 会把前端传入的任意 `messageId` 写入事件。
- **影响**：用户可把自己的事件挂到他人消息 ID 上，污染采纳口径与后续 bad case 分析（不能读取内容，但统计会失真）。
- **修复**：事件接口校验「消息存在且 `message.sessionId == sessionId`」；或客户端事件不落 `messageId`，只落会话级归属。

### P2-3 · 客户端中断没有 `answer_failed` 事件

- **证据**：`answer_failed` 只在模型错误、MCP 工具错误与 SSE 超时时记录；用户主动停止/断开连接不产生失败事件。
- **影响**：失败率与放弃率偏低；06 风险清单中的「SSE 断连」仍缺一条数据。
- **建议**：在 `emitter.onCompletion` / `onError` 中按「未收到 done」补记，注意与正常完成去重。

### P2-4 · 统计与列表仍有内存全量路径

- **证据**：`FeedbackApplicationService.stats` 用 `feedbacks.find(...)` 拉取窗口内全部反馈后内存聚合；`SessionApplicationService.list` 仍 `findAll()` 后内存过滤。
- **影响**：演示规模可接受，数据增长后变慢；属于既有 P2-2 backlog，不是本轮回归。
- **建议**：反馈统计下推 SQL 聚合；会话列表与分页一并进下一迭代。

### P2-5 · `PiiMasker` 不覆盖 15 位老身份证

- **证据**：身份证规则只匹配 18 位；15 位老身份证可能被 16–19 位银行卡规则误判或漏过。
- **影响**：低概率隐私漏检；建议补 15 位规则与单测。

### P2-6 · V11 迁移是原地修改

- **证据**：`V11__answer_feedback.sql` 在未提交状态下新增了 `retrieval_json`、`reasons_csv`、`handle_status`、`agent_name` 等列。
- **影响**：若该迁移已在任何共享/本地 MySQL 执行过，Flyway 会因 checksum 变化报错；生产尚未部署则无影响。
- **要求**：合入前确认目标库 `flyway_schema_history` 中没有 V11；若已执行，把新增列拆到 V13，不改 V11。

## 4. 合入前仍未验证的项

1. **MySQL 存量库迁移**：V1–V10 已有数据时执行 V11/V12，检查旧会话 `created_by=NULL`、旧消息默认 `ANSWER/0`、反馈表外键与索引。
2. **浏览器 E2E**：登录 → 智能体 → 对话 → 反馈/撤销 → 严格拒答 → 历史恢复 → 401/403/404。
3. **真实 Redis 限流**：20 次/分钟反馈、120 次/分钟客户端事件在 Redis 可用与不可用两种状态下的行为。
4. **05 §4 第 7、8 条自动化**：删除会话级联反馈、普通/MCP 拒答分支的 `done` 契约仍只有代码核对，没有自动化用例。

## 5. 复审结论

- **代码层面**：P0 全部通过；B0 达到本轮目标；无新的阻断级缺陷。
- **合入条件**：修复 P1-1；完成 MySQL 存量迁移演练与浏览器 E2E；补 P2-1、P2-2 的修复或明确接受；确认 V11 未被任何库执行。
- **提交建议**：按 07 第 6 节拆分提交（V11/V12 迁移、M2 安全、KQ-1 修正、B0、前端、测试），当前不要一次性提交全部改动。

## 6. 第二次复评记录（2026-11-18）

### 6.1 修复项核对

| 项 | 结论 | 核对依据 |
|---|---|---|
| P1-1 `/sessions/stats` 权限 | ✅ 已修复 | `SessionController` 加 `@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")`；`SessionSecurityTest.statsRequiresAdminRole` 覆盖 VIEWER 403、DEVELOPER 200 |
| P2-1 角色大写 | ✅ 已修复 | `UserContext` 紧凑构造器统一 `trim().toUpperCase(Locale.ROOT)`、去空去重；小写角色测试通过 |
| P2-2 事件消息归属 | ✅ 已修复 | `SessionApplicationService.ensureMessageInSession` 校验消息存在且 `sessionId` 匹配；`SessionSecurityTest.eventMessageMustBelongToSession` 覆盖本人 200、跨会话 404 |
| P2-3 断连失败事件 | ⚠️ 基本修复 | 普通/MCP 分支均在 `onCompletion` 未完成时补记 `answer_failed(disconnect)`，超时/模型错误不重复；但见 6.2 的边界问题 |
| P2-5 15 位身份证 | ✅ 已修复 | `PiiMasker` 新增 15 位规则并保留前六后三；单测覆盖 |
| P2-6 V11/V13 拆分 | ✅ 已修复 | V11 恢复原内容；V13 新增 `retrieval_json`、`handle_status`、`reasons_csv`、`agent_name`；本地 MySQL `flyway_schema_history` 显示 V11 checksum `407078699`、V12/V13 均成功 |
| 额外事件属性上限 | ✅ 已修复 | `MAX_PROPERTY_KEYS` 调到 16；`answer_completed` 11 个属性可落库，单测覆盖 |

### 6.2 复评项闭环情况

1. **`onCompletion` 置位**：✅ 已修复。普通与 MCP 分支均改为 `getAndSet(true)`；完成回调同样先用 `getAndSet(true)` 判定，断连后不再重复记录事件。
2. **MCP 断连后的 completed 事件**：✅ 已修复。客户端断连/超时后完成回调只落库、不记 `answer_completed`，同一轮不会出现成功 + 失败两条事件。
3. **管理端 E2E 与限流压测**：⚠️ 部分闭环。限流内存降级边界已有单测：`FeedbackRateLimiterTest`（20 次/分钟）与 `ClientEventRateLimiterTest`（120 次/分钟）；真实 Redis 压力验证与 admin-web 浏览器 E2E 仍在 backlog。
4. **05 §4 第 7、8 条自动化**：✅ 已补。`SessionDeleteCascadeIntegrationTest` 覆盖会话/消息/反馈级联删除；`AgentRuntimeServiceDonePayloadTest` 覆盖普通/MCP/拒答 `done` 契约。

### 6.3 独立复验结果

| 项 | 结果 |
|---|---|
| `mvn -q -f backend/pom.xml test` | **42 个测试通过，0 失败 0 错误**（实现方已把 07 报告更新为 42） |
| `npm --prefix user-web run build` | 通过（exit 0） |
| `npm --prefix admin-web run build` | 通过（exit 0） |
| 本地 MySQL `flyway_schema_history` | V11 checksum `407078699`、V12、V13 均 success |
| 工作区 | `git diff` sha256 `dec30aec…5beb`，HEAD 仍 `2abefbf`，未提交 commit |

**复评结论：通过。** P0/P1 已全部闭环，无合入阻断项。剩余仅 §6.2 第 3 项（真实 Redis 限流压测、管理端 E2E）与统计内存路径，作为发布前建议/后续 backlog。建议仍按 07 第 6 节拆分提交。
