# 迭代 01 · KQ-1 + B0 技术评审记录

| 项目 | 内容 |
|---|---|
| 评审对象 | [05 范围收口决策](05-scope-decision-kq1-b0.md)、[KQ-1 PRD](../prd/KQ-1-feedback-and-no-answer.md)、当前工作区实现 |
| 评审基线 | `HEAD = 2abefbf` + 未提交改动（后端 chat/agent/portal、双前端、V11、docs） |
| 快照时间 | 2026-11-18 00:05（工作区仍可能被并行修改，合入前需按本文复查） |
| 评审角色 | 开发（方案与实现）、产品（口径与验收）、测试（验证方案） |
| 评审结论 | **有条件通过（方案与 KQ-1 主链路）**；当前实现**不得合入/上线**，P0 未闭环；B0 未开始，迭代验收不通过 |

> **修复状态（2026-11-18）**：本文 P0-1～P0-5、P1-1～P1-9 已在实现中闭环，验证与残余限制见 [07-实现报告](07-implementation-report.md)；正式复审仍以本文清单为准。

## 0. 一句话结论

KQ-1 的反馈、回答模式、拒答与双端界面已经能跑通主链路；但**会话读接口仍然全量开放、续聊不校验归属、历史无主会话仍可被反馈**，这三条让「反馈归属」本身失去意义；同时 B0 埋点尚未开始，测试只有 4 个反馈单测。方案方向通过，实现退回补齐后复评。

## 1. 评审方法与基线验证

| 项 | 结果 |
|---|---|
| `mvn test` | 通过：7 个测试（上下文 2、模块边界 1、反馈服务 4），见 `/tmp/kq1-mvn-test2.log` |
| Flyway V11（H2） | 通过：11 个迁移全部执行，`answer_feedback` 成功 |
| `user-web` 构建 | 通过：`tsc --noEmit && vite build` |
| `admin-web` 构建 | 通过：产物 641.93 kB，仍有 >500 kB 警告（既有问题） |
| 代码审阅 | 后端 chat/agent/portal、V11、user-web、admin-web 逐文件核对 |
| 未验证 | MySQL 存量库 V11 升级、SSE 真机链路、双账号越权、反馈并发、埋点（尚未实现） |

## 2. 完成度盘点（对照 05 第 3 节）

| 里程碑 | 内容 | 状态 | 说明 |
|---|---|---|---|
| M1 KQ-1 后端 | V11、反馈 upsert/查询、回答模式、拒答落库、`done` 补字段 | **基本完成** | 反馈/统计/拒绝/低置信/`done` 均有实现；缺 STRICT 数据集限制、检索诊断、`handle_status` |
| M2 安全归属 | 会话列表/详情/消息属主校验、历史无主仅管理员、管理端角色限制 | **未完成** | 读接口无角色/属主校验；续聊不校验；历史无主反馈仍放行 |
| M3 用户端 | 反馈条、原因弹层、拒答卡片、状态恢复 | **基本完成** | 反馈/拒答/低置信 UI 已有；历史消息仍走管理端接口 |
| M4 管理端 | 反馈列表/统计、回答模式配置 | **基本完成** | 运行追踪页已加反馈区块，智能体编辑页已加回答模式；脱敏、分页、权限联动未完成 |
| M5 B0 后端 | V12 `product_events`、9 个事件、异步幂等写入 | **未开始** | 无表、无 Recorder、无事件 |
| M6 B0 前端 | `agent_opened`、`answer_copied`、`citation_downloaded` | **未开始** | 双前端均无事件上报 |
| M7 验收 | 测试矩阵、迁移演练、双端构建、发布清单 | **20%** | 仅 4 个反馈单测；无集成/E2E/MySQL 迁移测试 |

## 3. 评审发现

### P0 · 阻断（合入/上线前必须闭环）

#### P0-1 会话读接口仍然全量开放，任意登录用户可读任意会话

**证据**

- [SessionController.java:44](../../backend/src/main/java/com/agentplatform/modules/chat/interfaces/rest/SessionController.java)、`:78`、`:104`：列表、详情、消息三个 `@GetMapping` 没有 `@PreAuthorize`，也没有属主过滤；只有删除接口是 `PLATFORM_ADMIN`。
- [SessionApplicationService.java:39](../../backend/src/main/java/com/agentplatform/modules/chat/application/service/SessionApplicationService.java)、`:55`、`:83`：`findAll()`、按 ID 直接 `get`，没有任何 `createdBy` 判定。
- [user-web/src/api.ts:142](../../user-web/src/api.ts)：用户端历史消息仍调用管理端 `/sessions/{id}/messages`，绕过了新加的反馈接口归属校验。

**影响**：05 验收第 5 条不成立；A 用 B 的会话 ID 可以读到完整对话，反馈归属形同虚设；上线即违反最小权限原则。

**修复要求**

1. 新增 `SessionAccessPolicy`：`PLATFORM_ADMIN`/`DEVELOPER` 全量；`VIEWER` 仅 `createdBy = 当前登录名`；`createdBy IS NULL` 仅 ADMIN/DEVELOPER。
2. 列表/详情/消息统一走该策略；非本人统一 404，避免存在性泄露。
3. 用户端历史消息改调 `/api/v1/user/sessions/{id}/messages`（或让管理端接口按策略支持本人），并补 401/403/404 集成测试。

#### P0-2 续聊不校验归属，且拒答分支会把历史会话「认领」给当前用户

**证据**

- [AgentRuntimeService.java:126](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java)：`existingSession` 查到后直接使用，没有比较 `createdBy`；B 传入 A 的 `sessionId` 后，A 的历史会被读入上下文，B 的消息会追加到 A 的会话。
- [AgentRuntimeService.java:193](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java)：拒答分支 `owner = existing.createdBy() != null ? existing.createdBy() : createdBy`，历史无主会话会被当前调用者写入归属，随后可反馈、可继续占用。

**影响**：这是比读接口更直接的会话劫持；拒答路径还制造了「先到先得」的归属抢占。

**修复要求**

1. `existingSession != null` 时必须校验 `createdBy`；非本人且非 ADMIN/DEVELOPER 返回 404/403。
2. 拒答分支不得改写已有会话的 `createdBy`；历史无主会话保持 NULL，仅管理端可见。
3. 补双账号续聊/拒答的集成测试。

#### P0-3 历史无主会话仍允许反馈，与 05 §2.3 冲突

**证据**

- [FeedbackApplicationService.java:221](../../backend/src/main/java/com/agentplatform/modules/chat/application/service/FeedbackApplicationService.java)：`createdBy == null` 时直接放行；注释也写明「历史会话按过渡策略放行」。
- 05 §2.3 明确：`created_by IS NULL` 的历史会话仅 `PLATFORM_ADMIN` / `DEVELOPER` 可见，**反馈只允许会话所有者提交**。

**影响**：任意登录用户可以对任何历史会话的回答点赞/点踩并留下问题，污染反馈数据与统计。

**修复要求**：`createdBy` 为空时用户端反馈返回 403/404；只有带归属的会话所有者可写。同步修改 KQ-1 PRD 的「过渡策略」表述。

#### P0-4 B0 埋点完全未开始

**证据**

- `backend/src/main/java`、`user-web/src`、`admin-web/src` 中检索 `product_events`、`ProductEvent`、`answer_copied`、`agent_opened` 均无结果。
- 迁移目录只有 `V11__answer_feedback.sql`，没有 V12；05 第 3 节 M5/M6 的交付物均未出现。

**影响**：05 验收第 9、10 条无法验证；后续 KQ-2 缺口看板、有效会话率、漏斗没有数据源。

**修复要求**：按 05 的 M5/M6 实现 V12、9 个事件、异步幂等写入、失败降级、客户端三类事件；补事件字典与对账测试。

#### P0-5 合入测试门槛未满足

**证据**

- 新增测试只有 [FeedbackApplicationServiceTest.java](../../backend/src/test/java/com/agentplatform/modules/chat/application/service/FeedbackApplicationServiceTest.java) 4 个单测；没有会话归属、SSE `done` 契约、拒答模型调用 0 次、V11 在 MySQL 存量库升级、并发反馈等测试。
- 05 验收第 12 条要求 `mvn test` + 双前端构建 + 关键接口 401/403/200；目前只满足前两项。

**修复要求**：按 05 第 3 节 M7 与 04 第 3.5 节的最低测试清单补齐；合入前在 CI 中跑通。

### P1 · 高（发布前必须闭环）

| 编号 | 发现 | 证据 | 影响与修复 |
|---|---|---|---|
| P1-1 | STRICT 只校验「绑定知识库」，没有限制纯关键词数据集 | [AgentApplicationService.java:89](../../backend/src/main/java/com/agentplatform/modules/agent/application/service/AgentApplicationService.java)、`:110`；05 §2.5 | 关键词路无阈值，几乎不会 0 命中，STRICT 拒答失效。保存时校验数据集检索模式仅 VECTOR/HYBRID；管理端下拉同步禁用并提示。 |
| P1-2 | 检索诊断未落库 | V11 无 `hitCount/topScore/mode/noHit` 字段；`session_messages` 只有 `answer_type/low_confidence`；05 §2.6 | KQ-2 无法定位「为什么没答上」。建议 V11 合并前补 `retrieval_json`（或 V12），运行时写入本轮命中数、top 分、模式、是否 0 命中。 |
| P1-3 | 反馈处理状态缺失 | V11 `message_feedback` 无 `handle_status`；05 §2.7 | B2 顺延时仍要改表。合并前补 `handle_status VARCHAR(16) NOT NULL DEFAULT 'OPEN'` 与索引；本轮可只读展示。 |
| P1-4 | 反馈从 DOWN 改为 UP 时，旧原因/备注不会被清空 | [MessageFeedbackEntity.java:41](../../backend/src/main/java/com/agentplatform/modules/chat/infrastructure/persistence/entity/MessageFeedbackEntity.java)、`:43`；[FeedbackRepositoryImpl.java:85](../../backend/src/main/java/com/agentplatform/modules/chat/infrastructure/persistence/repository/FeedbackRepositoryImpl.java)、`:93` | `reasonsJson/comment` 为 null 时，MyBatis-Plus 默认 `NOT_NULL` 更新策略会跳过该列，旧值残留。修复：字段加 `updateStrategy = FieldStrategy.ALWAYS`，或改用显式 `UpdateWrapper.set(...)`；补「DOWN→UP 清空原因/备注」测试。 |
| P1-5 | 管理端反馈列表全量加载 + N+1 查询 + 无总数/分页控件 | [FeedbackApplicationService.java:156](../../backend/src/main/java/com/agentplatform/modules/chat/application/service/FeedbackApplicationService.java)、`:316`；admin Sessions 只请求 `size=10` | 反馈量大时内存与查询次数线性增长。修复：SQL 分页 + count，agentName 由会话/反馈表冗余或 join；前端补分页控件。 |
| P1-6 | 隐私与输出安全未落地 | `FeedbackListItem` 直接返回 `userId/questionPreview/answerPreview/comment`；admin 直接渲染；无 `PiiMasker`、无 HTML 转义、无限流、无提交 INFO 日志 | 05 要求「接口展示脱敏」，PRD 要求「HTML 转义、20 次/分钟限流、INFO 日志（不含正文）」。修复：DTO 层统一掩码，输出转义，Redis 限流，日志只记 rating/agent。 |
| P1-7 | 用户端历史消息仍走管理端接口 | [user-web/src/api.ts:142](../../user-web/src/api.ts) | P0-1 修复后该接口会对普通 VIEWER 收紧，用户端历史会 403。修复：改用 `/user/sessions/{id}/messages`，或让管理端接口按属主策略兼容。 |
| P1-8 | 身份取 JWT `subject`，OIDC 场景可能不是登录名 | RuntimeController、UserPortalController、UserFeedbackController 均用 `jwt.getSubject()` | 本地 JWT 的 subject 是用户名，OIDC 可能是 opaque sub，导致归属/反馈人与用户表不一致。建议统一 `CurrentUser`：优先 `uid`/`preferred_username`，回退 subject。 |
| P1-9 | 拒答 SSE 内容已补齐，但无回归测试 | [AgentRuntimeService.java:212](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java) 已加 `done.content`；[user-web/src/App.tsx:485](../../user-web/src/App.tsx) 使用 `done.content` 填充拒答卡片 | 评审过程中已修复；需补 SSE 契约测试，避免以后被删改。 |

### P2 · 中低（可下一轮或随修复顺手处理）

| 编号 | 发现 | 建议 |
|---|---|---|
| P2-1 | V11 比 05 多出 `question_preview/answer_preview`，且预览 120 字、PRD 写 80 字 | 产品确认是否保留快照列；统一截断口径并回写 05/PRD；若保留，注明隐私与保留期 |
| P2-2 | `FeedbackController` 的 `@RequestParam Instant from/to` 未做格式校验/测试 | 补 `@DateTimeFormat(iso = DATE_TIME)` 与参数测试，或在文档限定 ISO-8601 |
| P2-3 | 反馈率分子按「反馈创建时间」、分母按「消息创建时间」 | 在窗口边界会产生轻微偏差；文档注明或改为按消息时间过滤 |
| P2-4 | 拒答分支重复会话保存逻辑，且未记录 runtime duration | 抽 `saveRefusal` 复用；补 `agent_runtime_duration` 或单独指标 |
| P2-5 | 管理端反馈列表无 total、无页码控件；原因过滤在内存完成 | 与 P1-5 一起做 SQL 分页；rating 参数统一大写 |
| P2-6 | 文档不一致 | 05 已生效，但 KQ-1 PRD 仍写「历史会话放行反馈」，01/02/03 仍是旧口径；PRD 与 01/02 需回写，README 已指向 05 |
| P2-7 | 反馈原因文案/标签：点踩六类 + 点赞三类，PRD 未明确点赞是否必填 | 产品确认「点赞原因可选」与「拒答文案固定」两个开放问题（PRD §8） |

## 4. 产品与测试确认项

### 4.1 产品确认（不阻断开发，但影响验收）

1. 点赞原因保持可选（当前实现）；拒答文案固定、不按智能体配置（当前实现）。
2. 是否保留反馈快照列 `question_preview/answer_preview`；若保留，截断长度统一为 80 还是 120。
3. 历史无主会话按 05 收紧为「仅管理员可见、不可反馈」，需要发布公告与客服话术。
4. VIEWER 在管理端只看自己的会话；演示脚本改用 admin/developer。

### 4.2 测试确认（合入前必须）

1. 双账号矩阵：列表/详情/消息/续聊/反馈的 401/403/404；历史无主仅管理员。
2. 反馈：DOWN 必选 1–2 原因、备注 200 字边界、重复提交更新、DOWN→UP 清空旧原因/备注、撤销、并发唯一约束。
3. 回答模式：STRICT 0 命中模型调用 0 次、HYBRID 低置信、`done` 三类字段在普通/MCP/拒答分支一致。
4. 迁移：V11 在 H2 与 MySQL（V1–V10 存量）升级，旧消息默认 `ANSWER/0`，旧会话 `created_by=NULL`。
5. 隐私：手机号/证件号/银行卡样本在管理端反馈列表与详情中被掩码；备注 HTML 不执行。
6. 埋点（M5/M6 完成后）：9 类事件落库、`event_id` 幂等、写失败不阻断、每日对账。
7. 前端：刷新/换设备反馈状态恢复；拒答卡片「留下问题」；移动端点击区 ≥40×40。

## 5. 评审结论

1. **方案与交互：通过。** KQ-1 的反馈模型、HYBRID/STRICT 策略、拒答卡片、管理端反馈区块与 05 的口径一致；双端构建通过。
2. **当前实现：有条件通过，不得合入/上线。** 条件为 P0-1 至 P0-5 全部闭环，P1-1 至 P1-9 在发布前闭环。
3. **迭代验收：不通过。** B0（M5/M6）未开始，05 验收第 9、10 条无实现；测试门槛未满足。
4. **建议顺序**：先补 M2 安全归属（1 天）→ KQ-1 P1 修正（1.5 天）→ B0 后端与前端（3.5 天）→ 测试与迁移演练（1.5 天）→ 复评。

## 6. 行动项

| # | 行动 | 负责人 | 预估 | 验收 |
|---|---|---|---|---|
| A1 | 会话读接口属主校验 + 管理端角色策略 + 历史无主仅管理员 | 开发 | 1 天 | 双账号 404/403；VIEWER 只见本人 |
| A2 | 续聊/拒答归属校验；禁止拒答分支认领历史会话 | 开发 | 0.5 天 | B 无法续聊 A；历史会话 `created_by` 不被改写 |
| A3 | 历史无主会话反馈改为拒绝 | 开发 | 0.25 天 | 无主会话反馈 403/404 |
| A4 | STRICT 数据集类型限制 + 检索诊断落库 | 开发 | 1 天 | 纯关键词数据集不能选 STRICT；消息带 `retrieval_json` |
| A5 | `handle_status` + 反馈清空字段修复 + 管理端列表分页/总数 | 开发 | 1 天 | DOWN→UP 无残留；分页 SQL 可验证 |
| A6 | 脱敏/HTML 转义/限流/INFO 日志 | 开发 | 0.5 天 | 敏感样本掩码；20 次/分钟 429；日志无正文 |
| A7 | B0 V12 + 9 事件 + 异步幂等 + 双端三类事件 | 开发 | 3.5 天 | 事件落库、重试幂等、失败不阻断 |
| A8 | 测试补齐（归属/反馈/拒答/SSE/迁移/埋点） | 测试 + 开发 | 1.5 天 | CI 全绿；测试矩阵执行记录 |
| A9 | 文档回写（PRD/01/02/03/05）与发布公告 | 产品 + 开发 | 0.5 天 | 口径一致；公告确认 |
| A10 | V11/V12 在 MySQL 存量库演练 | 开发 | 0.5 天 | 升级成功、旧数据默认值正确 |

## 7. 证据索引

- 范围与口径：[05-scope-decision-kq1-b0.md](05-scope-decision-kq1-b0.md)
- 需求：[KQ-1 PRD](../prd/KQ-1-feedback-and-no-answer.md)
- 独立评审：[04-three-lens-independent-review.md](04-three-lens-independent-review.md)
- 会话接口：[SessionController.java](../../backend/src/main/java/com/agentplatform/modules/chat/interfaces/rest/SessionController.java)、[SessionApplicationService.java](../../backend/src/main/java/com/agentplatform/modules/chat/application/service/SessionApplicationService.java)
- 运行时：[AgentRuntimeService.java](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java)
- 反馈服务：[FeedbackApplicationService.java](../../backend/src/main/java/com/agentplatform/modules/chat/application/service/FeedbackApplicationService.java)
- 反馈持久化：[FeedbackRepositoryImpl.java](../../backend/src/main/java/com/agentplatform/modules/chat/infrastructure/persistence/repository/FeedbackRepositoryImpl.java)
- 迁移：[V11__answer_feedback.sql](../../backend/src/main/resources/db/migration/V11__answer_feedback.sql)
- 用户端：[user-web/src/App.tsx](../../user-web/src/App.tsx)、[user-web/src/api.ts](../../user-web/src/api.ts)
- 管理端：[admin-web/src/pages/Sessions.tsx](../../admin-web/src/pages/Sessions.tsx)、[admin-web/src/pages/Agents.tsx](../../admin-web/src/pages/Agents.tsx)

## 8. 会签

| 角色 | 姓名 | 意见 | 日期 |
|---|---|---|---|
| 开发 | Claude | 有条件通过；P0 未闭环，不得合入 | 2026-11-18 |
| 产品 | | | |
| 测试 | | | |
