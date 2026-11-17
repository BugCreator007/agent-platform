# 迭代 01 · KQ-1 + B0 实现报告（复审材料）

| 项目 | 内容 |
|---|---|
| 实现范围 | KQ-1 回答反馈与无答案识别、B0 埋点基线、会话读接口越权修复 |
| 依据 | [05 范围收口](05-scope-decision-kq1-b0.md)、[06 技术评审](06-technical-review-kq1-b0.md)、[KQ-1 PRD](../prd/KQ-1-feedback-and-no-answer.md) |
| 基线 | `2abefbf` + 未提交改动 |
| 日期 | 2026-11-18 |
| 状态 | 实现完成，待产品/测试复审；**未提交 commit** |

## 0. 基线与改动范围

| 项 | 值 |
|---|---|
| 起始 commit | `2abefbf` |
| 协作会话 00:07 记录的 `git diff` sha256 | `fef4004a2cdb8cdefddddf0d58a6f1604c16a0d3a8ea48d1e83ad7c6b80f7df0` |
| 完成时 `git diff` sha256 | `dec30aecac2a8279b87b7046026567f4669669f5c0c9ad2c91cb52b2ab085beb` |
| 已跟踪文件改动 | 32 个文件，+1394 / -120 |
| 未跟踪新增 | 41 个路径（chat 反馈/查询类、`insight` 模块、portal 用户接口、V11/V12/V13、双端测试、迭代文档） |

> 起始哈希只覆盖已跟踪文件；新增反馈/埋点文件当时尚未跟踪。完成时哈希同样只覆盖已跟踪文件，新增文件清单可用 `git status --short` 对照。接手后未观察到其他会话的并行修改。

## 1. 交付摘要

本轮把评审记录里的 P0/P1 全部落地，并完成 B0：

- **安全归属（P0-1～P0-3）**：新增 `UserContext` / `CurrentUser` / `SessionAccessPolicy`；会话列表、详情、消息、续聊、反馈统一校验 `created_by`；历史无主会话仅 ADMIN/DEVELOPER 可见，普通用户不可读、不可续聊、不可反馈。
- **KQ-1 修正（P1-1～P1-9）**：STRICT 限制纯关键词数据集；助手消息落 `retrieval_json`；反馈表补 `handle_status`；修复 DOWN→UP 旧原因/备注残留；管理端反馈列表改为 SQL 分页 + 总数 + 原因检索列 + 智能体名称快照；PII 脱敏、20 次/分钟限流、INFO 日志；OIDC 身份取 `preferred_username`；拒答 SSE 保留 `content`。
- **B0 埋点**：新增 `insight` 上下文与 V12 `product_events`；after-commit 异步写入、`event_id` 幂等、失败降级；9 类事件；客户端事件白名单与限流；管理端事件统计接口与页面卡片。
- **复审修正**：`/sessions/stats` 仅 ADMIN/DEVELOPER；角色统一大写；客户端事件 `messageId` 必须属于该会话；用户断开 SSE 补记 `answer_failed(disconnect)`，`onCompletion` 使用 `getAndSet` 去重且已断连时跳过 `answer_completed`；PII 增加 15 位老身份证；V11 已在本地执行，新增列拆到 V13，V11 恢复原 checksum；`answer_completed` 属性上限从 10 调到 16（含检索诊断共 11 个键）。
- **双端界面**：用户端反馈条、原因弹层、拒答卡片、复制/引用下载埋点；管理端反馈分页、处理状态、事件统计、回答模式数据集限制提示。

## 2. 关键文件

### 后端

| 区域 | 文件 |
|---|---|
| 身份与访问 | `common/security/UserContext.java`、`framework/security/CurrentUser.java`、`chat/application/service/SessionAccessPolicy.java` |
| 会话与运行时 | `SessionApplicationService.java`、`SessionController.java`、`AgentRuntimeService.java`、`UserSessionController.java` |
| 反馈 | `FeedbackApplicationService.java`、`FeedbackRateLimiter.java`、`FeedbackRepositoryImpl.java`、`MessageFeedbackEntity.java`、`V11__answer_feedback.sql`、`V13__feedback_retrieval_and_status.sql` |
| 隐私 | `common/text/PiiMasker.java` |
| 埋点 | `modules/insight/**`、`V12__product_events.sql`、`ProductEventRecorder.java`、`UserEventController.java` |
| 智能体 | `AgentApplicationService.java`（STRICT 数据集校验）、`Agent.java`、`AgentSummary.java`、`AgentEntity.java` |

### 前端

| 工程 | 文件 |
|---|---|
| user-web | `src/api.ts`（用户消息接口、反馈、事件）、`src/App.tsx`（反馈/拒答/复制/引用埋点） |
| admin-web | `src/pages/Sessions.tsx`（反馈分页、处理状态、事件统计）、`src/pages/Agents.tsx`（STRICT 数据集限制）、`src/api.ts` |

## 3. 验证结果

| 验证项 | 结果 |
|---|---|
| `mvn test` | **42 个测试全部通过**（认证上下文、模块边界、反馈、归属、严格模式、PII、埋点、分页、统计权限、事件消息归属、done 契约、级联删除、限流、迁移） |
| V11/V12/V13 H2 迁移 | 通过（`ApplicationContextTests` 启动时执行 13 个迁移） |
| MySQL 存量迁移演练 | **通过**：本地 MySQL 8.4 在已执行 V11 的库上校验 checksum（`407078699` 一致），成功应用 V12/V13；38 条旧会话 `created_by=NULL`、62 条旧消息 `ANSWER/0`、1 条旧反馈 `OPEN` |
| 浏览器 E2E（user-web） | **通过**：viewer 登录 → 对话 → 低置信提示 → 复制埋点 → 点踩/刷新恢复/撤销 → STRICT 拒答卡片 → 留下问题 → 历史恢复；反馈与事件接口均 200 |
| 真实 Redis 限流 | 内存降级路径单测覆盖 20/120 次边界；Redis 可用环境下接口正常；未做 20/120 次/分钟压力验证 |
| 埋点实库复验 | **通过**：修复 `answer_completed` 属性超限后，真实对话落库 `session_started/message_sent/answer_completed`；反馈与客户端 `agent_opened` 正常 |
| `user-web` 构建 | 通过 |
| `admin-web` 构建 | 通过（650.88 kB，仍有 >500 kB 既有警告）；管理端页面未做浏览器 E2E |
| 删除会话级联反馈 | 表外键已建；仍未写自动化用例 |

## 4. 对照 05 §4 验收清单

| # | 验收项 | 状态 | 说明 |
|---|---|---|---|
| 1 | 点赞/点踩刷新仍保留；同一消息+用户唯一；可修改/撤销 | ✅ | 浏览器 E2E：点踩 → 刷新恢复 → 撤销；单元 + 持久化测试 |
| 2 | 点踩 1–2 原因；备注 >200 拒绝；他人消息 404/403 | ✅ | `FeedbackApplicationServiceTest`、`SessionSecurityTest` |
| 3 | STRICT 0 命中拒答且模型调用 0 次 | ✅ | `AgentRuntimeServiceStrictModeTest` + 浏览器拒答卡片 |
| 4 | HYBRID 0 命中低置信 | ✅ | 浏览器显示「未检索到相关资料」；`low_confidence` 落库 |
| 5 | A 看不到 B；按 ID 读消息 404；历史无主用户端不可见 | ✅ | `SessionSecurityTest`（含历史无主 DEV 可读） |
| 6 | 管理端按智能体/时间/评分/原因筛选；列表与统计一致；VIEWER 403 | ✅ | 分页/原因过滤集成测试 + 权限测试（含 stats 403） |
| 7 | 删除会话消息与反馈级联清理 | ✅ | `SessionDeleteCascadeIntegrationTest` |
| 8 | 普通与 MCP 分支 `done` 均含 messageId/answerType/lowConfidence | ✅ | `AgentRuntimeServiceDonePayloadTest`（普通/MCP/拒答统一构造器）+ 浏览器拒答验证 |
| 9 | 9 类事件落库、归属正确 | ✅ | 事件仓储/记录器测试；客户端事件浏览器上报 200 |
| 10 | 写入失败不阻断、`event_id` 重试幂等 | ✅ | `ProductEventRecorderTest` |
| 11 | V11/V12/V13 在 H2 与 MySQL 升级 | ✅ | H2 13 个迁移；本地 MySQL 存量库 V11→V13 演练通过 |
| 12 | `mvn test` + 双前端构建 + 401/403/200 | ✅ | 42 测试 + 双构建 + `SessionSecurityTest` |

## 5. 复审重点（请按 06 逐项核对）

1. **P0-1**：`SessionApplicationService` 是否所有读路径都经过 `SessionAccessPolicy`；管理端与用户端是否都不可越权。
2. **P0-2**：运行时续聊/拒答是否不再改写已有 `createdBy`；历史无主是否无法被认领。
3. **P0-3**：`FeedbackApplicationService.requireOwnedSession` 对空归属是否返回 404。
4. **P1-4**：`MessageFeedbackEntity` 的 `reasons_json/comment` 是否使用 `FieldStrategy.ALWAYS`；`FeedbackRepositoryIntegrationTest` 是否覆盖 DOWN→UP 清空。
5. **P1-1**：`AgentApplicationService.validateStrictDatasets` 是否拒绝关键词数据集；管理端下拉是否同步禁用。
6. **B0**：`ProductEventRecorder` 是否 after-commit、异步、失败吞掉、`event_id` 唯一；客户端事件是否只允许三类且属性白名单。
7. **迁移**：V11 是否保持原 checksum（本地 `407078699`）；V12 `product_events`、V13 新增列是否按顺序应用。
8. **复审新增修正**：`/sessions/stats` 是否 ADMIN/DEVELOPER；`UserContext` 是否统一大写角色；客户端事件 `messageId` 是否校验属于会话；断连是否补记 `answer_failed(disconnect)`；PII 是否覆盖 15 位身份证。

## 6. 已知限制与后续

- `handle_status` 本轮只落库与展示，没有处理动作接口（B2 洞察迭代再做）。
- 事件保留期未实现清理任务（建议 180 天，进 backlog）。
- 备注 XSS 依赖前端 React 自动转义（无 `dangerouslySetInnerHTML`）；如需后端强制转义，需避免双重转义。
- `FeedbackStats.feedbackRate` 的分子按反馈创建时间、分母按消息创建时间，窗口边界会有轻微偏差。
- 删除会话级联反馈与普通/MCP/拒答 `done` 契约已补自动化；admin-web 页面未做浏览器 E2E。
- 真实 Redis 限流只验证了可用性，未做 20/120 次/分钟压力验证。
- B2 会话洞察页、KQ-2 缺口看板、KQ-4 真实评测均未开始。
- 本轮未提交 commit；工作区包含原 KQ-1 改动与本轮修复，提交前建议按「V11/V12/V13 迁移、M2 安全、KQ-1 修正、B0、前端、测试」拆分。

## 7. 复审命令

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test
```

```bash
cd user-web && npm run build
```

```bash
cd admin-web && npm run build
```
