# 迭代 01 技术设计文档

> **2026-11-18 范围收口**：本轮只交付 KQ-1 + B0 埋点；A1 完整会话管理、B2 洞察顺延。本文档中 A1/B2 的设计保留作后续参考；迁移编号（V11 反馈、V12 事件）、归属字段（`created_by`）与反馈口径以 [05-scope-decision-kq1-b0.md](05-scope-decision-kq1-b0.md) 为准。

> 范围：PRD-A1 用户端会话管理、PRD-B0 产品埋点基线、PRD-B1 消息级反馈、PRD-B2 会话洞察（只读版）
> 需求依据：[需求评审记录](01-requirements-review.md)、[产品迭代思考](../product-iteration.md)
> 版本：v1.0 · 2026-11-17 · 作者：开发（Claude）· 状态：待技术评审

## 0. 结论摘要

本轮做三件事：**把会话变成「有主」的**、**把用户行为变成「事件」**、**把反馈变成「可看的洞察」**。技术上新增一个 `insight` 限界上下文承载反馈、事件与洞察读模型；`chat` 模块补齐会话归属与用户会话用例；`portal` 暴露用户端接口；两个前端分别增加会话侧边栏与「反馈与洞察」页。

关键取舍：

1. **不新建用户体系**：复用平台账号，`chat_sessions.user_id` 为唯一归属凭证；无主历史会话不迁移、用户端不可见。
2. **事件与反馈分表**：`product_events` 只增不改、不存原文；`session_feedback` 存用户当前反馈状态；洞察从两张表 + 会话/消息表聚合。
3. **洞察是读模型**：`insight` 模块不反向依赖 `chat` 的领域对象，用独立 Mapper XML 做跨表只读聚合，避免模块循环依赖。
4. **埋点不阻断主流程**：事件写入独立事务 + 失败吞掉 + WARN 与失败计数；对话不因埋点失败而失败。
5. **权限收紧**：反馈与洞察仅 `PLATFORM_ADMIN`、`DEVELOPER` 可见；`VIEWER` 在管理端只能看自己的会话；用户端一律按所有权校验，非本人统一 404。

## 1. 背景与目标

### 1.1 现状

- 用户端只有「智能体目录 + 单会话聊天」，会话快照存在 `localStorage`，无服务端历史。
- `chat_sessions` 无 `user_id`，管理端消息接口不校验归属，存在越权读取。
- 没有消息级反馈，没有产品事件表；`usage_records` 只有建表、没有写入。
- 运营概览只能回答「有多少会话」，不能回答「会话有没有用」。

### 1.2 本轮目标

- 用户能跨设备看到并管理自己的会话，A/B 账号互相隔离。
- 每次对话的关键节点落事件，形成「打开 → 提问 → 回答 → 追问/采纳/反馈」的漏斗。
- 用户能对单条回答反馈，运营能在后台看到未解决问题与高频问题。
- 所有涉及用户原文的接口默认脱敏，事件表不落原文。

### 1.3 非目标

- 开放 API（C1）、模板中心（E3）、终端用户注册与部门租户（P1-8）。
- bad case 转评测用例（B3）、知识缺口回收（B4）、AI 主题聚类。
- 真实评测执行器（P1-6）、真实成本归集（P1-5）；洞察页不展示这两类结论。

## 2. 关键设计决策（ADR）

| 编号 | 决策 | 选项与取舍 | 结论 |
|---|---|---|---|
| ADR-1 | 会话归属 | 新建终端用户体系 / 复用平台账号 | 复用平台账号，`user_id` 存 `users.id`；`user_id IS NULL` 视为历史无主会话 |
| ADR-2 | 反馈归属模块 | 放 `chat` / 新建 `insight` / 放 `observe` | 新建 `insight`：反馈、事件、洞察三者同属「产品分析」上下文，避免 `chat` 越做越胖 |
| ADR-3 | 洞察取数方式 | 调 `chat` 查询 API / 独立只读 SQL | 独立只读 Mapper XML。分析型读模型允许跨表查询；`insight` 不依赖 `chat`，避免与 `chat → insight` 的埋点依赖成环 |
| ADR-4 | 事件写入时机 | 同步强一致 / 异步队列 / 独立事务尽力而为 | 同步 + `REQUIRES_NEW` 独立事务，失败吞掉；当前规模足够，后续量大再换消息队列 |
| ADR-5 | 会话删除 | 物理删除 / 软删除 | 软删除 `deleted_at`；用户不可见，管理端可查，事件与反馈不悬挂 |
| ADR-6 | 「新建会话」 | 预创建空会话 / 首条消息时创建 | 首条消息时创建；用户端「新建会话」只是清空当前上下文，避免空会话污染列表 |
| ADR-7 | 事件是否存原文 | 存原文 / 只存 ID 与度量 | 只存 ID、长度、耗时、条数；原文只在 `session_messages`，按权限脱敏展示 |
| ADR-8 | 历史会话迁移 | 自动归属 / 不迁移 | 不迁移。无法可靠判断归属，自动归属会制造越权；上线公告说明 |
| ADR-9 | VIEWER 权限 | 维持全量只读 / 收紧 | 收紧：会话与洞察仅 ADMIN/DEVELOPER；VIEWER 管理端只看自己的会话，用户端正常使用 |

## 3. 总体方案

```text
┌──────────────┐        HTTP / SSE         ┌─────────────────────────────────────────────┐
│  user-web    │ ────────────────────────▶ │ portal（/api/v1/user/*）                     │
│  会话侧边栏   │                           │  ├─ UserSessionController ──▶ chat.UserSessionService
│  反馈/复制    │                           │  ├─ UserFeedbackController ─▶ insight.FeedbackApplicationService
└──────────────┘                           │  └─ UserEventController ────▶ insight.ProductEventRecorder
                                           │  └─ UserPortalController ───▶ chat.AgentRuntimeService
┌──────────────┐        HTTP               ├─────────────────────────────────────────────┤
│  admin-web   │ ────────────────────────▶ │ chat.interfaces.rest（运行追踪，按角色过滤） │
│  反馈与洞察   │                           │ insight.interfaces.rest（/insight, /feedback）│
└──────────────┘                           └─────────────────────────────────────────────┘
                                                              │
                                     ┌────────────────────────┴───────────────────────┐
                                     │ chat：会话归属、用户会话用例、运行时埋点           │
                                     │ insight：反馈、事件、洞察读模型、PII 脱敏          │
                                     └────────────────────────┬───────────────────────┘
                                                              ▼
                     MySQL：chat_sessions / session_messages / session_feedback / product_events
```

模块依赖方向（新增）：

```text
portal ──▶ chat（application/runtime、application/service）
portal ──▶ insight（application/service、application/query）
chat   ──▶ insight（application/service: ProductEventRecorder）
observe ─▶ chat（既有 query，不变）
insight ─▶ 无业务模块依赖（只读 SQL 聚合 chat 表，ADR-3）
```

`chat` 与 `insight` 是单向依赖，不会形成 Spring Modulith 循环；`insight` 的跨表 SQL 只出现在 `infrastructure/persistence/mapper/InsightQueryMapper.xml`，并在类注释中标注「分析型读模型」。

## 4. 模块与代码变更清单

### 4.1 新增 `modules/insight`

```text
insight/
├── domain/model/
│   ├── Feedback.java                反馈聚合（rating、reasons、comment、status）
│   ├── FeedbackReason.java          六类原因枚举（编码 + 中文文案）
│   ├── FeedbackStatus.java          OPEN / RESOLVED / IGNORED
│   ├── ProductEvent.java            事件记录（名称、ID、属性 JSON、发生时间）
│   └── ProductEventName.java        事件名枚举 + 客户端白名单
├── domain/repository/
│   ├── FeedbackRepository.java
│   └── ProductEventRepository.java
├── application/service/
│   ├── FeedbackApplicationService.java   提交/更新/撤回/后台筛选/处理
│   ├── ProductEventRecorder.java         对外埋点入口（校验 + 脱敏 + 失败兜底）
│   └── ProductEventWriter.java           独立事务写入（REQUIRES_NEW）
├── application/query/
│   ├── InsightApplicationService.java    指标、漏斗、未解决、高频问题
│   └── *.java                            InsightSummary / FunnelStage / UnresolvedItem / TopQuestion / FeedbackView
├── infrastructure/persistence/
│   ├── entity/FeedbackEntity.java
│   ├── entity/ProductEventEntity.java
│   ├── mapper/FeedbackMapper.java
│   ├── mapper/ProductEventMapper.java
│   ├── mapper/InsightQueryMapper.java    只读聚合（XML 在 resources/mapper/）
│   ├── repository/FeedbackRepository.java
│   ├── repository/ProductEventRepository.java
│   └── InsightDataSeeder.java            演示数据（app.demo.seed=true 时）
├── interfaces/rest/InsightController.java    /api/v1/insight/*、/api/v1/feedback/*
└── package-info.java
```

`application/service` 与 `application/query` 需要 `package-info.java` 加 `@NamedInterface("api")`，供 `chat` 与 `portal` 跨模块调用；`infrastructure` 不对外暴露。

### 4.2 `chat` 模块

| 文件 | 变更 |
|---|---|
| `domain/model/ChatSession.java` | 增加 `userId`、`createdAt`、`updatedAt`、`deletedAt` 字段 |
| `domain/repository/SessionRepository.java` | 增加 `findByUserId(...)`、`findByIdAndUserId(...)`、`softDelete(id)`、`touch(id, updatedAt)` |
| `application/service/UserSessionService.java`（新增） | 用户会话列表、消息、重命名、软删除；所有权校验 |
| `application/service/SessionApplicationService.java` | 管理端列表/详情/消息按角色过滤；删除保留物理删除仅 ADMIN |
| `application/service/SessionAccessPolicy.java`（新增） | 统一判定：ADMIN/DEVELOPER 全量；其他角色仅本人；无归属历史会话仅管理端可见 |
| `application/runtime/AgentRuntimeService.java` | `stream(request, userContext)`；新会话写 `user_id`；续聊校验所有权；埋点 `session_started/message_sent/answer_completed/answer_failed` |
| `interfaces/rest/RuntimeController.java` | 从 JWT 取当前用户并传入运行时 |
| `interfaces/rest/SessionController.java` | 加角色权限；返回 `userId/updatedAt/deletedAt`；主题预览脱敏 |
| `infrastructure/persistence/entity/ChatSessionEntity.java` | 映射 `user_id/updated_at/deleted_at` |
| `infrastructure/persistence/repository/ChatSessionRepository.java` | 新查询与软删除实现 |
| `infrastructure/persistence/SessionSeedData.java` | 种子会话显式 `userId = null`，由 `InsightDataSeeder` 在演示环境归属给 `viewer` |

### 4.3 `portal` 模块

| 文件 | 变更 |
|---|---|
| `interfaces/rest/UserSessionController.java`（新增） | 会话列表、消息、重命名、删除 |
| `interfaces/rest/UserFeedbackController.java`（新增） | 提交/撤回反馈 |
| `interfaces/rest/UserEventController.java`（新增） | 客户端事件上报 |
| `interfaces/rest/UserPortalController.java` | 对话接口传入当前用户；续聊归属校验 |

### 4.4 `framework` / `common`

| 文件 | 变更 |
|---|---|
| `framework/security/CurrentUser.java`（新增） | 从 `SecurityContextHolder` 读取 `uid`、`sub`、`roles`，缺失时抛 401 |
| `common/text/PiiMasker.java`（新增） | 手机号、身份证、银行卡掩码；纯函数，无 Spring 依赖 |
| `system/application/auth` | 无需改动；JWT 已有 `uid`、`sub`、`roles` 声明 |

### 4.5 前端

| 工程 | 变更 |
|---|---|
| `user-web/src/api.ts` | 会话/消息/反馈/事件 API 与类型；`streamChat` 携带会话 ID |
| `user-web/src/App.tsx` | 会话侧边栏、新建/切换/重命名/删除、历史消息加载、反馈 UI、复制与引用下载埋点 |
| `user-web/src/styles.css` | 侧边栏、反馈弹层、移动端抽屉样式 |
| `admin-web/src/modules.ts` | 新增 `insight` 模块「反馈与洞察」 |
| `admin-web/src/pages/Insights.tsx`（新增） | 指标卡、漏斗、未解决问题、高频问题、反馈列表 |
| `admin-web/src/pages/index.ts`、`App.tsx` | 注册页面与路由 |
| `admin-web/src/pages/Sessions.tsx` | 新增「用户」「反馈」列；删除标记筛选（ADMIN/DEVELOPER） |
| `admin-web/src/api.ts` | 洞察/反馈类型与 `patch` 方法 |

## 5. 数据设计

### 5.1 Flyway V11 迁移

> 当前最新为 `V10__knowledge_citations.sql`。本轮实现前需先处理工作区中未提交的 RAG 改动（P1-2/P1-3），保证 V11 迁移与检索改动分开提交、分开回滚。

```sql
-- 1) 会话归属与用户端会话管理
ALTER TABLE chat_sessions ADD COLUMN user_id VARCHAR(64) NULL;
ALTER TABLE chat_sessions ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE chat_sessions ADD COLUMN deleted_at TIMESTAMP NULL;
UPDATE chat_sessions SET updated_at = created_at;
CREATE INDEX idx_sessions_user_updated ON chat_sessions (user_id, updated_at);
CREATE INDEX idx_sessions_deleted ON chat_sessions (deleted_at);

-- 2) 消息级反馈
CREATE TABLE session_feedback (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    rating VARCHAR(8) NOT NULL,
    reasons VARCHAR(255),
    comment VARCHAR(1000),
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    handled_by VARCHAR(120),
    handled_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_feedback_message_user UNIQUE (message_id, user_id),
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES session_messages(id) ON DELETE CASCADE
);
CREATE INDEX idx_feedback_session ON session_feedback (session_id);
CREATE INDEX idx_feedback_status_created ON session_feedback (status, created_at);
CREATE INDEX idx_feedback_rating_created ON session_feedback (rating, created_at);

-- 3) 产品埋点事件
CREATE TABLE product_events (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_name VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    session_id VARCHAR(64),
    agent_id VARCHAR(64),
    message_id VARCHAR(64),
    properties_json TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_events_name_time ON product_events (event_name, occurred_at);
CREATE INDEX idx_events_session ON product_events (session_id);
CREATE INDEX idx_events_agent_time ON product_events (agent_id, occurred_at);
CREATE INDEX idx_events_user_time ON product_events (user_id, occurred_at);
```

**字段约定**

| 表.字段 | 说明 |
|---|---|
| `chat_sessions.user_id` | `users.id`；NULL = 历史无主会话，仅管理端可见 |
| `chat_sessions.updated_at` | 每次会话汇总保存时由应用写入，用于列表排序与「最近活跃」 |
| `chat_sessions.deleted_at` | 软删除时间；用户列表过滤 `IS NULL`，管理端可按需包含 |
| `session_feedback.rating` | `up` / `down` |
| `session_feedback.reasons` | 原因编码，复用 `StringListTypeHandler`（U+001F 分隔）；仅 `down` 必填 |
| `session_feedback.comment` | 纠错文本，≤500 字；入库前原样保留，接口输出脱敏 |
| `session_feedback.status` | `open` / `resolved` / `ignored` |
| `product_events.properties_json` | 受限 JSON，≤1 KB；不落原文 |

**约束说明**

- `chat_sessions.user_id` 不建外键：OIDC 用户可能不在本地 `users` 表，外键会阻断登录；归属校验在应用层完成。
- `session_feedback` 对消息建外键并 `ON DELETE CASCADE`：管理端物理删除会话时反馈随之清理，事件表保留统计事实。
- `product_events` 不建外键：事件是日志性质，不能因会话删除而丢失漏斗事实。

### 5.2 历史数据与演示数据

- 现有 30 条种子会话 `user_id = NULL`，生产历史会话同样为 NULL；上线后用户端不可见，管理端可见。
- `InsightDataSeeder`（`app.demo.seed=true` 且 `product_events` 为空时执行）：
  1. 查找 `viewer` 账号，将 `user_id IS NULL` 的演示会话归属到该账号；
  2. 为近 7 天会话生成 `session_started / message_sent / answer_completed` 事件；
  3. 生成约 12 条反馈（含 4 条 👎 与 2 条已处理）和 8 条采纳事件，保证洞察页非空。
- 生产环境 `app.demo.seed=false` 时初始化器不执行。

### 5.3 数据保留

- `product_events` 目标保留 180 天；本轮不实现清理任务，写入 backlog（P2）。
- `session_feedback` 随会话生命周期；软删除会话的反馈不再进入洞察窗口（SQL 过滤 `deleted_at IS NULL`）。
- 不提供原文导出接口；如需合规导出，走管理员数据库工单。

## 6. 接口设计

### 6.1 用户端接口（`/api/v1/user/*`，均要求登录 + 所有权校验）

| 方法 | 路径 | 说明 | 成功 | 主要错误 |
|---|---|---|---|---|
| GET | `/sessions?agentId=&page=1&size=20` | 我的会话列表，按 `updated_at` 倒序 | 200 分页 | 400 分页非法 |
| GET | `/sessions/{id}/messages` | 会话消息（含当前用户反馈状态、引用） | 200 列表 | 404 非本人/不存在 |
| PATCH | `/sessions/{id}` | 重命名 `{title}` | 200 会话摘要 | 400 标题非法；404 |
| DELETE | `/sessions/{id}` | 软删除 | 200 | 404 |
| POST | `/messages/{messageId}/feedback` | 提交/更新反馈 | 200 反馈视图 | 400 参数非法；404 非本人 |
| DELETE | `/messages/{messageId}/feedback` | 撤回反馈 | 200 | 404 |
| POST | `/events` | 客户端事件上报 | 200 | 400 事件名不在白名单/属性超限 |
| POST | `/chat:stream` | 流式对话（既有） | SSE | 403 智能体未开放；404 会话非本人 |

**会话列表响应**

```json
{
  "success": true,
  "code": "OK",
  "message": "ok",
  "data": {
    "items": [
      {
        "id": "s_ab12cd34",
        "agentId": "ag_01",
        "agentName": "售后助手",
        "title": "订单退款进度怎么查？",
        "status": "success",
        "turns": 3,
        "lastMessageAt": "2026-11-17T10:23:00Z",
        "createdAt": "2026-11-17T10:20:00Z",
        "feedback": { "up": 1, "down": 0 }
      }
    ],
    "page": 1,
    "size": 20,
    "total": 37
  }
}
```

**消息响应（节选）**

```json
{
  "id": "msg_9f2c1d",
  "sessionId": "s_ab12cd34",
  "role": "assistant",
  "content": "退款将在 1–3 个工作日原路退回……",
  "tokens": 186,
  "model": "deepseek-chat",
  "contextCount": 3,
  "citations": [{ "documentId": "doc_01", "name": "退款政策.pdf", "url": "/api/v1/documents/doc_01/download", "sourceType": "FILE" }],
  "feedback": { "rating": "down", "reasons": ["incomplete"], "comment": "没有说明跨行退款时效" },
  "createdAt": "2026-11-17T10:21:10Z"
}
```

**反馈请求**

```json
{ "rating": "down", "reasons": ["wrong", "incomplete"], "comment": "时效与政策不一致" }
```

约束：`rating` 必填；`down` 至少 1 个原因；原因必须在六类枚举内；`comment` ≤500 字；同一 `message_id + user_id` 唯一，重复提交视为更新。

**事件上报请求**

```json
{ "name": "answer_copied", "sessionId": "s_ab12cd34", "messageId": "msg_9f2c1d", "agentId": "ag_01", "properties": { "length": 186 } }
```

约束：`name` 只允许 `agent_opened`、`answer_copied`、`citation_downloaded`；服务端忽略请求中的 `userId`；`properties` 只允许标量值，序列化后 ≤1 KB；带 `sessionId` 时校验归属。

### 6.2 管理端接口

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/api/v1/insight/summary?days=7` | 指标卡 | ADMIN/DEVELOPER |
| GET | `/api/v1/insight/funnel?days=7` | 会话漏斗 | ADMIN/DEVELOPER |
| GET | `/api/v1/insight/unresolved?days=7&limit=20` | 未解决问题 | ADMIN/DEVELOPER |
| GET | `/api/v1/insight/top-questions?days=7&limit=20` | 高频问题 | ADMIN/DEVELOPER |
| GET | `/api/v1/feedback?days=&rating=&reason=&status=&agentId=&page=&size=` | 反馈列表 | ADMIN/DEVELOPER |
| PATCH | `/api/v1/feedback/{id}` | 标记 `resolved/ignored/open` | ADMIN/DEVELOPER |
| GET | `/api/v1/sessions` | 运行追踪（既有，增加用户/反馈字段与角色过滤） | ADMIN/DEVELOPER 全量；VIEWER 仅本人 |
| GET | `/api/v1/sessions/{id}/messages` | 会话消息（既有，增加所有权判定） | 同上 |
| DELETE | `/api/v1/sessions/{id}` | 物理删除（既有） | PLATFORM_ADMIN |

**洞察指标响应**

```json
{
  "window": { "from": "2026-11-10T16:00:00Z", "to": "2026-11-17T16:00:00Z", "days": 7 },
  "sessions": 120,
  "effectiveSessions": 74,
  "effectiveRate": 61.7,
  "firstAnswerUsefulRate": 72.5,
  "negativeFeedbackRate": 8.3,
  "avgTurns": 2.4,
  "abandonRate": 18.2,
  "answerFailed": 5,
  "feedback": { "up": 40, "down": 12, "open": 5 },
  "adoption": { "copied": 18, "citationDownloaded": 7 }
}
```

**未解决问题条目**

```json
{
  "feedbackId": "fb_01",
  "messageId": "msg_9f2c1d",
  "sessionId": "s_ab12cd34",
  "agentId": "ag_01",
  "agentName": "售后助手",
  "reasonLabels": ["内容不完整"],
  "question": "订单 138****5678 退款多久到账？",
  "answerPreview": "退款将在 1–3 个工作日原路退回……",
  "comment": "没有说明跨行时效",
  "status": "open",
  "createdAt": "2026-11-17T10:23:00Z"
}
```

所有 `question / answerPreview / comment` 在 DTO 组装时经过 `PiiMasker`，不返回原文。

### 6.3 SSE 协议

不新增事件类型。现有 `meta` 与 `done` 已包含 `sessionId`（见 [AgentRuntimeService](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java)），用户端在首轮 `meta` 中取到会话 ID 后写入本地状态并刷新会话列表。续聊时请求体带 `sessionId`，服务端校验归属。

### 6.4 错误与状态码

| 场景 | 状态码 | code |
|---|---|---|
| 参数非法（标题、反馈、事件名） | 400 | `VALIDATION_ERROR` |
| 未登录/Token 失效 | 401 | `UNAUTHENTICATED` |
| 管理端角色不足 | 403 | `FORBIDDEN` |
| 用户端非本人会话/消息、资源不存在 | 404 | `NOT_FOUND` |
| 事件属性过大、频率超限 | 400 / 429 | `VALIDATION_ERROR` / `FORBIDDEN` |
| 内部错误 | 500 | `INTERNAL_ERROR` |

用户端对「非本人」统一返回 404，不暴露资源是否存在；管理端角色不足明确返回 403。

## 7. 关键流程

### 7.1 首条消息创建会话

```text
user-web                portal                runtime                  insight
   │ POST /user/chat:stream │                     │                        │
   │───────────────────────▶│ 取 CurrentUser       │                        │
   │                        │────────────────────▶│ 创建 session(user_id)   │
   │                        │                     │ 保存 user 消息           │
   │                        │                     │ record(session_started)─▶│ REQUIRES_NEW
   │                        │                     │ record(message_sent)────▶│
   │                        │                     │ RAG/工具/模型流           │
   │◀──── SSE meta(sessionId)─────────────────────│                        │
   │◀──── SSE delta…──────────────────────────────│                        │
   │                        │                     │ 保存 assistant 消息      │
   │                        │                     │ record(answer_completed)▶│
   │◀──── SSE done────────────────────────────────│                        │
```

### 7.2 续聊与越权校验

```text
POST /user/chat:stream {agentId, message, sessionId}
  → 查 session
  → 不存在或 user_id != 当前用户 → 404 "会话不存在"
  → deleted_at != null → 404
  → 通过后沿用该会话，追加消息
```

管理端调试（`/api/v1/runtime/sessions:stream`）以当前管理员身份创建会话，`source=admin_debug`。

### 7.3 提交反馈

```text
POST /user/messages/{messageId}/feedback
  → 查消息（必须是 assistant）
  → 通过消息找到 session，校验 session.user_id = 当前用户且未删除
  → 校验 rating/reasons/comment
  → upsert session_feedback（message_id + user_id 唯一）
  → record(feedback_submitted)
  → 返回当前反馈视图
```

### 7.4 洞察查询

```text
GET /api/v1/insight/summary?days=7
  → 校验角色（ADMIN/DEVELOPER）与窗口（1..90）
  → InsightQueryMapper 聚合：
       sessions        = chat_sessions 窗口内且 deleted_at IS NULL
       effective       = 用户消息 ≥2 OR 反馈 up OR 采纳事件
       firstAnswerUseful = 首答未被踩 且（追问 OR 首答赞 OR 采纳）
       negativeRate    = down 反馈数 / assistant 消息数
       abandonRate     = 24h 无后续互动且有提问的会话 / 有提问会话
  → DTO 组装时 PII 脱敏
```

## 8. 埋点方案

### 8.1 事件字典

| 事件 | 来源 | 触发点 | properties | 说明 |
|---|---|---|---|---|
| `session_started` | 服务端 | 新会话落库后 | `agentId`, `source` | source = user_portal / admin_debug |
| `message_sent` | 服务端 | 用户消息落库后 | `turnIndex`, `length` | 不记录内容 |
| `answer_completed` | 服务端 | 助手消息落库后 | `latencyMs`, `tokens`, `model`, `mock`, `citationCount`, `toolCount` | mock = 是否演示流 |
| `answer_failed` | 服务端 | 模型/工具/超时失败 | `stage`, `errorCode` | stage = model / tool / timeout |
| `feedback_submitted` | 服务端 | 反馈 upsert 成功后 | `rating`, `reasons`, `hasComment` | 撤回不删事件，洞察以反馈表状态为准 |
| `session_deleted` | 服务端 | 软删除后 | `turns` | 不记录标题 |
| `agent_opened` | 客户端 | 进入某智能体聊天页 | `agentId` | 漏斗第一层 |
| `answer_copied` | 客户端 | 复制助手回答成功 | `messageId`, `length` | 采纳口径之一 |
| `citation_downloaded` | 客户端 | 引用下载成功 | `messageId`, `documentId`, `sourceType` | 采纳口径之一 |

### 8.2 写入可靠性

- `ProductEventRecorder` 负责白名单、长度、归属校验，然后调用 `ProductEventWriter`（`@Transactional(REQUIRES_NEW)`）。
- 写入异常在 Recorder 内捕获：`log.warn` + `insight_event_record_failed_total` 指标 + 返回成功；对话主流程不受影响。
- 客户端事件每用户限流 120 次/分钟（Redis 计数，复用现有 Redis；Redis 不可用时降级放行并记录指标）。
- 事件属性只允许字符串/数字/布尔，嵌套对象拒绝；序列化后 ≤1 KB。

### 8.3 指标公式（与产品对齐）

设窗口 `[from, to)`，只统计 `deleted_at IS NULL` 的会话：

| 指标 | 公式 |
|---|---|
| 会话数 | 窗口内创建的会话数 |
| 有提问会话 | 至少 1 条 user 消息的会话 |
| 有效会话 | `user消息数 ≥ 2` 或 `存在 rating=up` 或 `存在 answer_copied/citation_downloaded` |
| 有效会话率 | 有效会话 / 有提问会话 |
| 首答有用率 | 首条 assistant 未被 down，且（会话有追问 或 首答获 up 或 会话有采纳） / 有首答的会话 |
| 负反馈率 | down 反馈数 / assistant 消息数 |
| 平均轮次 | user 消息总数 / 有提问会话数 |
| 放弃率 | 有提问、24h 内无追问/赞/采纳/反馈、且最后消息早于 24h 的会话 / 有提问会话；失败会话不计入放弃，单列 `answerFailed` |
| 高频问题 | 窗口内会话首条 user 消息归一化（去空白/标点、英文小写、截断 60 字）后分组计数 |

统计时区：SQL 用 UTC 时间边界；页面展示按 Asia/Shanghai。

## 9. 权限与安全

### 9.1 权限矩阵

| 能力 | PLATFORM_ADMIN | DEVELOPER | VIEWER | 终端用户（当前复用平台账号） |
|---|---|---|---|---|
| 用户端会话/反馈/事件 | 本人 | 本人 | 本人 | 本人 |
| 管理端会话列表/消息 | 全量 | 全量 | 仅本人 | 不可访问 |
| 管理端删除会话 | 是 | 否 | 否 | 否 |
| 反馈列表/处理 | 是 | 是 | 否 | 否 |
| 洞察指标/漏斗/高频问题 | 是 | 是 | 否 | 否 |

实现方式：用户端由 `UserSessionService` / `FeedbackApplicationService` 强制所有权；管理端由 `@PreAuthorize` + `SessionAccessPolicy` 双重保证。非本人会话统一 404，避免存在性泄露。

### 9.2 隐私

- 事件表不含原文；`properties_json` 禁止写入 message/answer 内容。
- `PiiMasker` 覆盖手机号（`1[3-9]\d{9}`）、18 位身份证、16–19 位银行卡；在洞察与反馈 DTO、管理端会话主题预览统一调用。
- 日志禁止打印问题原文、纠错文本、Token、Cookie；埋点失败日志只打印事件名与异常类型。
- 审计：反馈提交、删除、状态更新由既有 `AuditLogFilter` 记录方法/路径/操作人/IP，不记录正文。

### 9.3 客户端事件防滥用

- 事件名白名单，未知事件 400。
- `userId` 服务端覆盖，客户端传值无效。
- 限流 120 次/分钟/用户；Redis 不可用时降级放行并记录 `insight_event_rate_limit_degraded_total`。
- `properties` 只允许标量，长度与键数量受限（≤10 个键）。

## 10. 前端设计

### 10.1 user-web

```text
App
├── Login（不变）
├── AgentCatalog（不变，增加「最近会话」入口）
└── Chat
    ├── SessionSidebar：按智能体筛选、列表、重命名、删除、新建
    ├── MessageList：历史消息、引用、复制、反馈按钮
    └── FeedbackPopover：六类原因多选 + 纠错文本 + 提交/撤回
```

关键行为：

- 首次进入聊天页不创建会话；发送首条消息后从 `meta.sessionId` 取会话 ID，`done` 后刷新列表。
- 切换会话时按 `sessionId` 拉取消息；切换智能体时保留当前草稿。
- 重命名使用行内编辑，`Enter` 保存、`Esc` 取消；删除弹二次确认。
- 复制成功 toast「已复制」，随后上报 `answer_copied`；事件失败静默。
- 移除 `localStorage` 对话快照；启动时清理旧键 `agent_user_chat_*`，避免用户误以为旧对话仍在。
- 移动端（<900px）侧边栏改为抽屉，底部按钮呼出。

### 10.2 admin-web

新增模块 `insight`（路径 `/insights`，分组「工作台」，名称「反馈与洞察」）：

- 顶部指标卡 6 个：有效会话、有效会话率、首答有用率、负反馈率、放弃率、平均轮次。
- 漏斗：打开智能体 → 创建会话 → 首答完成 → 有效会话（含转化率）。
- Tab 1「未解决问题」：原因筛选 + 时间窗口 + 列表 + 详情抽屉（脱敏问答上下文、处理状态）。
- Tab 2「高频问题」：归一化问题、出现次数、负反馈次数、涉及智能体。
- Tab 3「全部反馈」：rating/status/agent/时间筛选，分页。
- 运行追踪页新增「用户」「反馈」列；VIEWER 只看到自己的会话，删除按钮仅 ADMIN 可见。

## 11. 性能与容量

- 会话列表：`(user_id, updated_at)` 索引 + `LIMIT/OFFSET`；当前规模足够，超过 10 万会话再换游标分页。
- 事件写入：单条 INSERT，独立事务；对话完成链路新增 <5ms（本地 MySQL 实测目标）。
- 洞察查询：全部限定时间窗口（≤90 天）与 `deleted_at IS NULL`；未解决问题按 `(rating, created_at)`、`(status, created_at)` 索引；高频问题在应用层对窗口内首问做归一化聚合（窗口上限 90 天，预计 <2 万会话，内存可控）。
- 大文本：消息内容不进入事件表；洞察 DTO 只返回 200 字摘要。
- SSE：不改变现有 180 秒超时与 12 条历史上限；埋点不占用 SSE 事件流。

## 12. 兼容、迁移与回滚

### 12.1 兼容性

- 新接口均为新增；既有管理端接口字段只增不减，前端可渐进升级。
- 用户端从 `localStorage` 迁移到服务端：旧会话不迁移，首次加载列表为空；旧本地键启动时清理。
- `ChatSession` 记录增加字段会影响所有构造点（运行时、种子、测试），实现时统一更新，保留旧字段顺序不变。

### 12.2 发布步骤

1. 合并/提交当前工作区未提交的 RAG 改动（P1-2/P1-3），确保 V11 独立提交。
2. 备份生产 MySQL（复用 `deploy/server/backup.sh`）。
3. 发布后端（Flyway 自动执行 V11）→ 验证 `/actuator/health`、登录、会话列表。
4. 发布 `user-web` 与 `admin-web`。
5. 用 admin/developer 账号走一遍：对话 → 复制 → 反馈 → 后台洞察。
6. 用两个账号验证会话隔离与 404。

### 12.3 回滚

- 代码可回滚到上一版本：新增列/表对旧代码无影响，旧代码忽略 `user_id/deleted_at`。
- Flyway 不回滚：**不执行 DROP/TRUNCATE/V11 反向 SQL**；如需彻底清理，单独走数据变更单。
- 事件表异常时可临时关闭埋点：新增配置 `app.insight.events-enabled=false`（默认 true），Recorder 直接返回，不影响对话。

## 13. 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `app.insight.events-enabled` | `true` | 埋点总开关；故障时快速关闭 |
| `app.insight.event-rate-limit` | `120` | 每用户每分钟客户端事件上限 |
| `app.insight.window-max-days` | `90` | 洞察查询最大窗口 |
| `app.demo.seed` | `true` | 演示数据总开关（既有）；生产置 false |

## 14. 测试方案

### 14.1 后端测试

| 测试类 | 覆盖 | 关键断言 |
|---|---|---|
| `UserSessionServiceTest` | 列表过滤、分页、重命名、软删除 | 非本人会话不可见；标题长度校验 |
| `SessionOwnershipIntegrationTest`（MockMvc + H2） | 用户端消息/续聊/重命名/删除 | 他人会话 404；本人 200；未登录 401 |
| `FeedbackApplicationServiceTest` | 提交、更新、撤回、原因必填、长度 | 唯一约束 upsert；对用户消息反馈 404 |
| `InsightMetricsTest` | 有效会话、首答有用率、放弃率、高频问题 | 构造固定事件与消息，断言数值 |
| `PiiMaskerTest` | 手机号/身份证/银行卡/无敏感信息 | 掩码正确且幂等 |
| `ProductEventRecorderTest` | 白名单、属性超限、写入失败降级 | 失败不抛异常；白名单外 400 |
| `ModularityTests`（既有） | 模块边界 | 新增 `insight` 后仍通过 |
| `ApplicationContextTests`（既有） | Flyway V11 + 上下文 | 启动成功 |

### 14.2 前端与端到端

- `user-web`：`npm run build`（含 `tsc --noEmit`）；手工验证会话侧边栏、反馈、复制、下载、移动端抽屉。
- `admin-web`：`npm run build`；手工验证洞察页、筛选、空状态、VIEWER 403/隐藏。
- E2E 手工脚本（本轮不引入自动化框架）：
  1. A 登录 → 对话 → 复制 → 点踩 → 刷新 → 列表标题正确 → 换浏览器仍存在；
  2. B 登录 → 看不到 A 的会话 → 用 A 的 sessionId 调接口得到 404；
  3. admin 登录 → 洞察页出现刚才的反馈与指标 → 标记已处理 → 未解决列表消失；
  4. 关闭 MySQL 写入（或临时把表改名）后对话仍成功，日志出现埋点失败 WARN。

### 14.3 验收追踪

| 需求 | 验收标准 | 验证方式 |
|---|---|---|
| A1 | 归属、列表、改名、删除、跨设备 | `SessionOwnershipIntegrationTest` + E2E 1/2 |
| B0 | 六类服务端事件、三类客户端事件、失败降级 | `ProductEventRecorderTest` + E2E 4 |
| B1 | 反馈提交/修改/撤回、后台筛选、脱敏 | `FeedbackApplicationServiceTest` + E2E 3 |
| B2 | 指标、漏斗、未解决、高频问题 | `InsightMetricsTest` + E2E 3 |

## 15. 任务拆分与排期（单人估算）

| 里程碑 | 内容 | 产出 | 估算 |
|---|---|---|---|
| M1 数据与骨架 | V11 迁移、`insight` 模块骨架、实体/仓储、`ChatSession` 扩字段 | 可启动、Modulith 通过 | 1.5 天 |
| M2 会话归属与 A1 后端 | `UserSessionService`、所有权校验、管理端角色过滤、`CurrentUser` | 用户端接口 + 集成测试 | 1.5 天 |
| M3 B1 反馈后端 | 反馈聚合、提交/撤回、后台筛选与处理、PII 脱敏 | 反馈接口 + 单测 | 1.5 天 |
| M4 B0 埋点与 B2 查询 | Recorder/Writer、事件字典、洞察 SQL、指标口径 | 洞察接口 + 指标测试 | 2.5 天 |
| M5 user-web | 侧边栏、历史消息、反馈、复制/下载埋点、样式 | 可用用户端 | 3 天 |
| M6 admin-web | 反馈与洞察页、模块注册、运行追踪列 | 可用管理端 | 2 天 |
| M7 联调与验收 | 演示数据、E2E、文档、构建、上线演练 | 验收记录、发布清单 | 1.5 天 |

合计约 13.5 人日，含缓冲按 2 周排期。提交边界：迁移/后端模块/用户端/管理端/文档分别提交，遵循 `.claude/skills/git-commit`。

## 16. 风险与应对

| 风险 | 概率 | 影响 | 应对 |
|---|---|---|---|
| 历史会话无主导致用户端「历史空了」 | 高 | 体验落差 | 上线公告；提供管理员手工归属工单；后续做账号认领 |
| VIEWER 权限收紧影响演示脚本 | 中 | 演示失败 | 演示账号改用 admin/developer；VIEWER 仅用于用户端 |
| 事件写入增加对话延迟 | 低 | P95 上升 | 独立事务 + 仅索引列；压测确认 <5ms；可一键关闭 |
| 洞察 SQL 在数据量大时变慢 | 中 | 页面超时 | 窗口上限 90 天 + 索引；后续加预聚合表 |
| 脱敏遗漏导致原文泄露 | 中 | 隐私事故 | 统一 `PiiMasker` + 单测；DTO 层强制；代码评审检查直接返回实体的路径 |
| 前端事件丢失低估有效会话 | 中 | 指标偏低 | 口径文档化；复制/下载失败仍可完成主流程；后续补后端导出行为 |
| 工作区未提交的 RAG 改动与 V11 混提 | 中 | 回滚困难 | 实现前先提交/隔离 RAG 改动；一个提交只做一件事 |

## 17. 开放问题（技术评审会确认）

1. 六类反馈原因标签的中文文案是否按建议执行（Q5）。
2. 会话删除是否接受「软删除 + 管理员可查」（Q6）。
3. VIEWER 管理端只保留「自己的会话」是否影响演示与验收（Q10）。
4. 高频问题是否需要排除「测试账号 / 内部账号」，本轮默认不排除，后续按账号标签过滤。
5. 洞察页是否需要展示「首答 P95 延迟」；本轮先不做，等真实埋点数据稳定后补。

## 18. 附录

### 18.1 原因标签编码

| 编码 | 文案 |
|---|---|
| `irrelevant` | 答非所问 |
| `wrong` | 信息有误 |
| `incomplete` | 内容不完整 |
| `outdated` | 信息过时 |
| `unsafe` | 不安全或冒犯 |
| `other` | 其他 |

### 18.2 事件名常量

```text
session_started
message_sent
answer_completed
answer_failed
feedback_submitted
session_deleted
agent_opened          （客户端）
answer_copied         （客户端）
citation_downloaded   （客户端）
```

### 18.3 相关文件

- 需求：[01-requirements-review.md](01-requirements-review.md)
- 评审：[03-technical-review.md](03-technical-review.md)
- 现有运行时：[AgentRuntimeService.java](../../backend/src/main/java/com/agentplatform/modules/chat/application/runtime/AgentRuntimeService.java)
- 现有会话接口：[SessionController.java](../../backend/src/main/java/com/agentplatform/modules/chat/interfaces/rest/SessionController.java)
- 数据迁移目录：[db/migration](../../backend/src/main/resources/db/migration)
- 开发约定：[CLAUDE.md](../../CLAUDE.md)
