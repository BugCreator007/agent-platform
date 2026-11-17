# KQ-1 回答反馈与无答案识别 · PRD

> **2026-11-18 调整**：本轮与 B0 埋点同期交付；读接口越权修复纳入本轮，历史无主会话（`created_by` 为空）改为仅 PLATFORM_ADMIN / DEVELOPER 可见、不可反馈。口径以 [05-scope-decision-kq1-b0.md](../iteration-01/05-scope-decision-kq1-b0.md) 为准。

| 项目 | 内容 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-11-17 |
| 负责人 | 产品（Claude） |
| 状态 | 待评审 |
| 所属场景 | 内部知识问答（`docs/product-iteration.md` 3.5 节） |
| 所属迭代 | 知识问答闭环 v1（第 5 节） |
| 依赖 | 无；为 KQ-2 知识缺口看板、KQ-4 评测回归提供数据 |

## 1. 背景与问题

当前平台已经能完成"提问 → 检索 → 带引用回答"，但回答质量没有任何回收机制。核对代码后确认四个事实：

1. **没有反馈入口**：`session_messages` 没有反馈字段，也没有反馈表；用户在回答下方无法表达"答得不对/没答上来"。
2. **无答案也会硬答**：运行时只要智能体绑定了知识库就会检索，但检索结果为空或全被阈值过滤时，依然把问题交给模型自由生成（`AgentRuntimeService` 只用 `contexts.size()` 做展示和 trace，不影响回答策略），容易产生无依据的编造。
3. **反馈无法挂到具体回答**：助手消息的 `msg_xxx` ID 在后端保存时才生成，SSE `done` 事件只返回 `citations/tokens/ms`，前端拿不到回答的消息 ID。
4. **无法校验反馈人**：`chat_sessions` 没有归属字段，任何登录用户都能读到任意会话的消息；反馈接口上线前必须补最小归属，否则会出现"给别人的回答点赞/点踩"。

结果：知识运营看不到失败样本，平台算不出有效回答率，KQ-2 的缺口聚类和 KQ-4 的 bad case 转化都没有数据源。

## 2. 目标与非目标

**目标**：让每一次"没答好"都变成一条结构化、可追踪、可归因的数据。

**成功指标**（上线两周后评估，先建立基线再定目标）：

- 反馈率 = 有反馈的助手消息 / 助手消息总数
- 好评率 = 点赞数 /（点赞 + 点踩）
- 无答案率 = 拒答消息 / 助手消息总数（仅严格模式智能体）
- 负反馈原因分布，以及"留下问题"的转化数

**非目标**（后续需求承接）：

- 不做知识缺口聚类与指派看板（KQ-2）
- 不做 bad case 转评测用例、版本对比（KQ-4）
- 不做人工客服接管、IM 通知、外部客户场景
- 不做用户级会话隔离的完整方案（P1-8），本次只补反馈所需的最小归属字段

## 3. 用户与用户故事

| 角色 | 诉求 |
|---|---|
| 提问员工 | 一键表达"有用/没用"，不打断对话；没答案时得到明确说明而不是编造 |
| 知识运营 | 看到负反馈明细与原因分布，知道该补哪类知识 |
| 平台管理员 | 控制智能体"无依据时是否必须拒答"，掌握整体质量 |

用户故事：

1. 作为员工，我能在回答下方点赞或点踩，刷新后状态仍在。
2. 作为员工，我能选一个原因说明为什么没用（答非所问、不完整、过时、依据不对、没有依据、其他）。
3. 作为员工，当知识库没有答案时，我得到一个明确的"暂时没有找到答案"卡片，并能把问题留下。
4. 作为知识运营，我能按智能体、时间、评分、原因筛选反馈，看到原问题、原回答和反馈人。
5. 作为管理员，我能为智能体选择回答模式：混合（现状）或严格（仅基于知识库）。

## 4. 方案总览

```text
数据层   message_feedback 新表 + chat_sessions.created_by + agents.answer_mode
运行时   done 事件补 assistantMessageId / answerType / lowConfidence
         STRICT 模式：无命中直接拒答，不调用模型
用户端   回答下方反馈条 + 原因弹层 + 拒答卡片（留下问题）
管理端   反馈列表与质量汇总 + 智能体回答模式配置
```

## 5. 详细需求

### 5.1 回答模式（智能体级配置）

新增智能体字段 `answer_mode`，两个取值：

- **HYBRID（默认，保持现状）**：无论是否检索到资料都交给模型回答。若绑定了知识库但本轮 0 条命中，回答正常生成，同时标记 `low_confidence=true`，前端在气泡顶部提示"未检索到相关资料，回答可能不准确"。
- **STRICT（严格引用）**：只依据知识库作答。
  - 检索结果经现有阈值过滤后为空 → **不调用模型**，直接生成固定拒答消息，`answer_type=REFUSAL`。
  - 有命中 → 正常生成，系统提示词追加约束："只能依据给定资料回答；资料不足时明确说明不知道，不得编造。"
  - 未绑定任何知识库的智能体不能选择 STRICT，保存时返回校验错误。

拒答文案（v1 固定，不开放编辑）：

> 我在已授权的知识库里没有找到能回答这个问题的内容。你可以换一种问法，或把问题留下，知识运营会补充相关资料。

判定口径：直接复用 `RetrievalResult.hits`（向量与混合模式已按数据集阈值过滤，关键词模式按得分 > 0 返回），"无答案" = hits 为空；本次不新增相似度阈值配置，阈值校准由 KQ-4 负责。

拒答同样计入会话轮次、写入 `session_messages`，会话 `status` 仍为 `success`，trace 增加一步：`knowledge / 严格模式未命中，拒答 / 0 tokens`。指标新增 `agent_runtime_refusals_total{agent}`。

### 5.2 反馈数据模型

新表 `message_feedback`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | VARCHAR(64) | 主键，`fb_` + 随机串 |
| `message_id` | VARCHAR(64) | 被反馈的助手消息，外键 `session_messages(id)`，级联删除 |
| `session_id` | VARCHAR(64) | 冗余，便于按会话聚合 |
| `agent_id` | VARCHAR(64) | 冗余，便于按智能体聚合 |
| `user_id` | VARCHAR(64) | 反馈人登录名 |
| `rating` | VARCHAR(8) | `UP` / `DOWN` |
| `reasons_json` | TEXT | 原因编码数组，DOWN 必填 1–2 个 |
| `comment` | VARCHAR(200) | 可选备注 |
| `follow_up_requested` | TINYINT | 是否点了"留下问题"，默认 0 |
| `created_at` | TIMESTAMP | 创建时间 |
| `updated_at` | TIMESTAMP | 最后修改时间 |

约束与规则：

- 唯一键 `(message_id, user_id)`：同一用户对同一条回答只有一条反馈，重复提交为更新（幂等），可撤销。
- 仅允许对 `role=assistant` 的消息反馈；对用户消息或不存在消息返回 400/404。
- 原因编码：
  - 点踩：`NOT_RELEVANT` 答非所问、`INCOMPLETE` 不完整、`OUTDATED` 信息过时、`WRONG_SOURCE` 依据不对、`NO_SOURCE` 没有依据、`OTHER` 其他。
  - 点赞（可选）：`SOLVED` 解决了问题、`CLEAR` 清晰易读、`SOURCE_USEFUL` 引用有用。
- 备注 ≤ 200 字，前端与后端都截断校验；提示用户不要填写敏感信息。
- 删除会话时，消息与反馈通过外键 `ON DELETE CASCADE` 一并清理。

会话归属最小改造：

- `chat_sessions` 增加 `created_by VARCHAR(64)`，门户发起新会话时从登录上下文写入当前用户名；管理端调试创建的会话同样写入操作人。
- 反馈鉴权：`created_by` 非空且不等于当前用户 → 403；`created_by` 为空的历史会话仅 `PLATFORM_ADMIN` / `DEVELOPER` 在管理端可见，用户端不可读、不可续聊、不可反馈（2026-11-18 范围收口后收紧，见 `docs/iteration-01/05-scope-decision-kq1-b0.md`）。
- 越权修复：`GET /api/v1/sessions`、`GET /api/v1/sessions/{id}`、`GET /api/v1/sessions/{id}/messages` 均校验 `created_by`；用户端历史消息改走 `GET /api/v1/user/sessions/{id}/messages`；非本人资源统一按不存在处理。

### 5.3 接口契约

用户端（均需登录，统一 `ApiResponse`）：

- `POST /api/v1/user/messages/{messageId}/feedback`
  - body：`{ "rating": "UP|DOWN", "reasons": ["INCOMPLETE"], "comment": "…", "followUpRequested": false }`
  - 校验：rating 合法；DOWN 的 reasons 数量 1–2 且编码合法；comment ≤ 200
  - 幂等 upsert，返回最新反馈
- `DELETE /api/v1/user/messages/{messageId}/feedback`：撤销自己的反馈
- `GET /api/v1/user/sessions/{sessionId}/feedback`：返回当前用户在该会话下的全部反馈，用于刷新/换设备恢复状态
- `GET /api/v1/user/sessions/{sessionId}/messages`：用户端历史消息（校验归属，替代直接调用管理端接口）
- `POST /api/v1/user/events`：客户端事件上报（`agent_opened` / `answer_copied` / `citation_downloaded`），服务端覆盖用户身份并按白名单过滤属性

管理端（`PLATFORM_ADMIN` / `DEVELOPER` / `EVALUATOR` 可读）：

- `GET /api/v1/feedback?agentId=&rating=&reason=&from=&to=&page=&size=`
  - 返回分页对象 `{ items, page, size, total }`：时间、智能体、会话、消息 ID、问题摘要、回答摘要（各截断 80 字）、反馈人、评分、原因、备注、是否留问题、处理状态
- `GET /api/v1/feedback/stats?agentId=&days=7`
  - 返回：反馈总数、反馈率、好评率、负反馈原因分布、拒答数、留问题数
- `GET /api/v1/insight/events/stats?days=7`
  - 返回事件名到数量的映射，用于验证埋点是否按预期落库

SSE `done` 事件新增字段（普通与 MCP 两条分支保持一致）：

- `assistantMessageId`：本轮助手消息 ID（后端需先生成 ID 再保存）
- `answerType`：`ANSWER` / `REFUSAL`
- `lowConfidence`：布尔值
- 原有 `citations`、`tokens`、`ms`、`mock` 不变

兼容性：旧前端忽略新增字段即可运行；`done` 不包含这些字段时反馈入口不显示。

### 5.4 用户端交互

反馈条（每条已完成的助手回答下方，流式生成中不显示）：

```text
[👍 有用] [👎 没用] [复制]
```

- 点赞：点击立即提交，图标高亮；再次点击撤销。若提供原因选择，作为可选二级操作，不阻塞提交。
- 点踩：点击弹出原因多选（1–2 个，必选），可选备注输入（≤200 字），"提交"后显示"已反馈，感谢你帮我们改进"。再次点击可修改或撤销。
- 提交失败（网络/鉴权）：保留选择并提示重试，不清空草稿。
- 刷新或换设备后，从 `GET /user/sessions/{id}/feedback` 恢复高亮状态。

拒答卡片（`answerType=REFUSAL`）：

```text
┌────────────────────────────────────┐
│ 暂时没有找到答案                     │
│ 我在已授权的知识库里没有找到能回答    │
│ 这个问题的内容……                    │
│ [留下问题]  [换个问法]               │
└────────────────────────────────────┘
```

- "留下问题"：展开文本框（可附一句补充说明），提交后写入一条 `DOWN + NO_SOURCE + follow_up_requested=1` 的反馈；成功后文案改为"已记录，知识运营会看到这个问题"。
- "换个问法"：聚焦输入框，不清空对话。
- 拒答消息不展示点赞按钮（可展示"没解决"反馈入口，即留下问题）。

可访问性：按钮有 `aria-label`；移动端点击区不小于 40×40；键盘可聚焦与操作。

### 5.5 管理端

1. **运行追踪（Sessions）**：增加"反馈"筛选项（全部/只有负反馈/按原因），会话详情里每条助手消息显示反馈徽标（👍/👎 + 原因）。
2. **回答质量区块**（放在运行追踪页顶部或独立小页，v1 先放运行追踪页）：
   - 卡片：反馈率、好评率、无答案数、留问题数（近 7/30 天，可按智能体筛选）
   - 负反馈原因分布（横向条形）
   - 最近负反馈列表，点击跳转会话详情
3. **智能体编辑页**：新增"回答模式"单选（混合/严格），严格模式旁边说明"未检索到资料时将直接拒答，不调用模型"；未绑定知识库时禁止选择并提示。
4. 反馈详情默认隐藏完整回答，点击"查看会话"再展示；列表中的提问与回答各截断 80 字。

权限：反馈列表与统计仅 `PLATFORM_ADMIN`、`DEVELOPER`、`EVALUATOR` 可读；`VIEWER` 不可见。智能体回答模式的修改沿用现有智能体编辑权限，并由审计过滤器记录。

### 5.6 指标与事件

本期不新增客户端埋点管道，指标全部从反馈表与消息表计算：

- 反馈率、好评率、点踩原因分布、无答案率、留问题数
- Micrometer：`agent_feedback_total{rating,agent}`、`agent_runtime_refusals_total{agent}`
- 反馈提交接口记录 INFO 日志（不含备注正文），不写审计正文（遵循"不记录完整提示词/回答"约定）

### 5.7 安全与非功能

- 反馈接口必须校验登录与消息归属；`user_id` 从 SecurityContext 获取，不接受前端传入。
- 备注字段输出到管理端时做 HTML 转义，防止 XSS。
- 写接口限流：同一用户每分钟 ≤ 20 次反馈写操作（复用 Redis 限流能力；未接入时先按内存计数降级）。
- 反馈提交 P95 < 200ms（不含网络）；单条 upsert。
- 数据库索引：`(agent_id, created_at)`、`(session_id)`、唯一键 `(message_id, user_id)`。

### 5.8 数据迁移与兼容

Flyway `V11__answer_feedback.sql`：

```sql
-- 会话归属（为反馈鉴权与后续用户隔离打底）
ALTER TABLE chat_sessions ADD COLUMN created_by VARCHAR(64);
CREATE INDEX idx_sessions_created_by ON chat_sessions (created_by);

-- 智能体回答模式：默认保持现有混合行为
ALTER TABLE agents ADD COLUMN answer_mode VARCHAR(16) NOT NULL DEFAULT 'HYBRID';

-- 助手消息类型与低置信标记
ALTER TABLE session_messages ADD COLUMN answer_type VARCHAR(16) NOT NULL DEFAULT 'ANSWER';
ALTER TABLE session_messages ADD COLUMN low_confidence TINYINT NOT NULL DEFAULT 0;

-- 反馈表
CREATE TABLE message_feedback (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    message_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    rating VARCHAR(8) NOT NULL,
    reasons_json TEXT,
    comment VARCHAR(200),
    follow_up_requested TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_message FOREIGN KEY (message_id) REFERENCES session_messages(id) ON DELETE CASCADE,
    CONSTRAINT uk_feedback_message_user UNIQUE (message_id, user_id)
);
CREATE INDEX idx_feedback_agent_time ON message_feedback (agent_id, created_at);
CREATE INDEX idx_feedback_session ON message_feedback (session_id);
```

回滚：反馈表与新增列均可通过新迁移删除；`answer_mode` 回滚即全部回到 HYBRID，行为与当前一致。种子数据不生成反馈记录。

> 实现口径（2026-11-18）：V11 保持已执行版本不变；`product_events` 使用 `V12`；复审新增的 `session_messages.retrieval_json`、`message_feedback.reasons_csv/handle_status/agent_name` 与状态索引使用 `V13__feedback_retrieval_and_status.sql`。本文 SQL 为需求初稿，与实现冲突时以 05 范围收口和迁移文件为准。

## 6. 验收标准

1. 员工对一条回答点赞或点踩后刷新页面，状态仍在；同一用户对同一消息重复提交只保留一条记录。
2. 点踩必须选择 1–2 个原因；备注超过 200 字被前后端同时拒绝；可撤销，撤销后图标恢复。
3. 严格模式智能体在知识库 0 命中时返回拒答卡片，且**模型请求次数为 0**（用本地演示模型/桩验证）；拒答消息可在管理端看到。
4. 混合模式 0 命中时正常回答，但带"可能不准确"提示，`low_confidence=1`。
5. 管理端可按智能体、时间、评分、原因筛选，列表与统计数字一致；`VIEWER` 访问返回 403。
6. 用户不能对他人的会话消息提交反馈（403）；历史会话（`created_by` 为空）按过渡策略放行并有记录。
7. 删除会话后，其消息与反馈一并删除，无残留。
8. 普通与 MCP 两条 SSE 分支的 `done` 均返回 `assistantMessageId/answerType/lowConfidence`，前端反馈可正常挂载。
9. `mvn test`、`admin-web` 与 `user-web` 构建通过；新增接口完成 401/403/200 验证。

## 7. 里程碑（单人估算）

| 阶段 | 内容 | 预估 |
|---|---|---|
| M1 | V11 迁移、实体/仓储、反馈 upsert 与查询接口、`done` 补字段 | 2 天 |
| M2 | 用户端反馈条、原因弹层、拒答卡片、状态恢复 | 2 天 |
| M3 | 管理端反馈列表/统计、回答模式配置、严格模式运行时 | 1.5 天 |
| M4 | 测试、401/403/200 验证、构建与回归 | 1 天 |

## 8. 风险与开放问题

- **过度拒答**：STRICT 只看检索是否命中，阈值偏高时会频繁拒答。上线后用真实问题观察拒答率，超过 30% 时优先校准阈值（KQ-4），不临时放宽规则。
- **严格模式与关键词检索**：关键词模式没有相似度阈值、几乎不会 0 命中，严格模式的拒答可能形同虚设。v1 建议严格模式智能体只绑定向量或混合检索的数据集；统一阈值放到 KQ-4。
- **反馈率天然偏低**：不设硬性 KPI，先看两周基线；后续可用"回答后轻提示"提升，但不在本期做。
- **归属字段只覆盖新会话**：历史会话仍可被任意登录用户反馈（过渡策略）；P1-8 上线后应重新收紧并补数据归属。
- **拒答后的"留下问题"没有服务承诺**：v1 只保证落库并在管理端可见，知识运营的响应流程属于 KQ-2。
- 待评审决策：点赞是否也要求原因（当前设计为可选）；严格模式拒答文案是否需要按智能体可配（当前固定）。

## 9. 变更记录

| 日期 | 版本 | 变更 |
|---|---|---|
| 2026-11-17 | v1.0 | 初稿，待评审 |
