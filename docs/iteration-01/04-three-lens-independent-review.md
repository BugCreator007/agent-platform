# 迭代 01 · 产品 / 架构 / 测试三视角独立评审

> 方法：在独立会话中启动三个互相隔离的只读 agent，分别从产品、技术架构、测试视角探索同一问题，最后由主会话对三方结论做交叉核对，并逐条抽查关键代码事实。三个 agent 互不可见，结论未经互相校准。
>
> 评审对象：`docs/product-iteration.md`（第 3.5、5 节）、`docs/iteration-01/01~03`、`docs/prd/KQ-1-feedback-and-no-answer.md`，以及当前工作区（基线提交 `2abefbf` + 未提交的 KQ-1 改动）。
>
> 日期：2026-11-17
>
> 定位：**评审输入**，不替代 `01-requirements-review.md`、`02-technical-design.md`、`03-technical-review.md`。冲突时以评审会决策为准，并回写正式文档。

---

## 0. 结论摘要

1. **方向成立，但当前存在两条口径不同的方案线，且同时占用 V11：**

   | | 迭代 01 设计线（01/02/03） | KQ-1 PRD 线（`docs/prd/` + 未提交代码） |
   |---|---|---|
   | 范围 | A1 会话管理 + B0 埋点 + B1 反馈 + B2 洞察 | 消息级反馈 + HYBRID/STRICT 回答模式 + 最小归属 |
   | 归属字段 | `chat_sessions.user_id`（users.id），完整所有权校验 | `chat_sessions.created_by`（登录名），只校验反馈 |
   | V11 内容 | user_id、软删、product_events、message_feedback | created_by、answer_mode、answer_type、low_confidence、message_feedback |
   | 原因标签 | 答非所问/信息有误/不完整/过时/不安全/其他 | NOT_RELEVANT/INCOMPLETE/OUTDATED/WRONG_SOURCE/NO_SOURCE/OTHER |
   | 备注长度 | ≤500 字 | ≤200 字（迁移也是 200） |
   | 估算 | 13.5 人日 | 6.5 人日 |

   两条线的文档都还没提交，V11 迁移也未提交——**现在是收口成本最低的时刻**；继续各写各的，迁移编号、字段语义和管理端页面都会互相打架。

2. **三个视角独立得出了同一组结论：**
   - 会话归属与越权读取是上线阻断项，不能只保护反馈接口；
   - 「无答案」不能简单等同于「检索结果为空」，关键词检索路没有阈值；
   - 反馈必须挂到具体的助手消息，`done` 事件必须返回消息 ID；
   - 真实评测（KQ-4）不是一个切片，本轮应当后置；
   - 埋点必须与反馈同期落地，否则缺口看板和北极星指标没有数据源；
   - 测试基础近乎为零，新链路必须自带测试，否则「改好了」无法证明。

3. **建议的收口方式（详见第 3 节）：**
   - 把 KQ-1 作为迭代 01 的 M1 垂直切片先行，但**把「会话读接口属主校验」并入本轮**；
   - V11 只保留一份迁移（建议以当前代码中的 `V11__answer_feedback.sql` 为准），`product_events`、会话列表字段放 V12；
   - B0/B2 紧随 KQ-1，按迭代 01 设计做；A1 的完整会话管理顺延，但越权修复不延期；
   - KQ-2/KQ-3/KQ-5 下一迭代；KQ-4 单独立项。

---

## 1. 三个视角的独立结论

### 1.1 产品视角

**一句话判断：条件成立。** 知识问答闭环值得做，但 `KQ-1~5 + 埋点 + 会话管理` 全量不是「一轮迭代」，而是一个季度；只有砍到「反馈 → 缺口清单 → 引用摘录」并先补会话身份与埋点，闭环才成立。

**用户与任务**

- 提问员工可感知的只有：提问、流式回答、引用、（未来的）反馈按钮。当前引用只能下载整篇文档，不能定位段落；历史只存在 `localStorage`，换设备即断（`user-web/src/App.tsx:27,239-281`）。
- 知识运营没有角色、没有入口：知识库页能上传/替换/检索测试，但看不到「哪些问题没答上、被踩」。
- 平台管理员只能按数据集把知识授权给智能体，不能按用户/部门控制文档可见性。

**四个「看起来闭环、实际断掉」的地方**

1. **无答案判定不可靠**：关键词路不经过阈值（`KnowledgeRetrievalService.java:114-123`），只要二元组命中就返回上下文；向量阈值 0.8 未校准，生产可能仍是哈希向量。运行时只把 topK 拼进提示词（`AgentRuntimeService.java:148-168`），没有「无可靠证据」信号。
2. **引用不是答案引用，是检索命中列表**：`MessageCitation` 只有文档四元组，丢掉 chunkIndex；运行时按文档去重后全塞进 `done.citations`（`AgentRuntimeService.java:151-166`），不校验模型是否真的使用。KQ-3 首先要回答「引用是否被答案使用」。
3. **反馈—缺口—评测—发布没有同一条主线**：四者是互不关联的表；没有统一 issue_id，做完仍是四个孤岛。
4. **会话管理被身份卡住**：`chat_sessions` 无属主字段；user-web 调的是管理端 `/sessions/{id}/messages`（`user-web/src/api.ts:112`），接口无属主校验。A1 不是「补个列表接口」，先要做隔离。

**优先级修订（与文档不一致处已标注）**

| 项 | 判断 | 理由 |
|---|---|---|
| B0 埋点 | 做，缩小到 8–10 个事件 | `usage_records` 表存在但代码零写入，别把建表当埋点 |
| KQ-1 | 做，拆分 | 本轮先做 👍/👎 + 原因 + 「没解决」+ 检索诊断落库；硬拒答只给 STRICT 模式，HYBRID 用软提示 |
| KQ-2 | 降为 Lite | 未命中/低分/负反馈列表 + Top 问题 + 负责人 + 状态；砍自动聚类、指派通知、效果验证 |
| KQ-3 | 拆 a/b | 3a：引用卡片显示文档名、版本、更新时间、命中片段、分块号；3b：页码/章节定位、旧引用失效后置（依赖 P1-4） |
| KQ-4 | **砍出本轮** | 评测集没有用例表，执行器是随机分，发布单无版本快照；真实评测是独立项目 |
| KQ-5 | 缩到最小 | 真实分块预览（当前 `Knowledge.tsx:380` 是硬编码占位）+ 生效/过期字段 + 引用处标「已过期」 |
| A1 会话管理 | 条件项 | 没有真实员工账号就砍；否则先补归属、属主校验、存量迁移，再做「我的会话」 |

**指标与埋点口径**

- 「周有效会话数」原口径把「追问」也算有效，但在知识问答里追问常常意味着首答失败，会把北极星做成虚荣指标。建议：自然周内至少一条回答满足「有引用或明确拒答」，且未被 👎/重问推翻的会话数。
- 建议事件：`qa_session_start`、`qa_question_submit`、`qa_retrieval`、`qa_answer_first_token`、`qa_answer_done`、`qa_answer_feedback`、`qa_citation_open`、`qa_session_end`、`qa_gap_status`。公共字段 `event_id/occurred_at/actor_id/session_id/message_id/agent_id/schema_version`。
- **问题原文不进通用事件表**，留在消息表并设权限与保留期，否则埋点会变成隐私池。
- 复盘固定看四项：反馈覆盖率、无答案率、引用打开率、缺口关闭率。

**反对意见**

1. 范围过大：七项至少动 6 个模块、5 张以上新表，单人 6–10 周；KQ-4/5 并行不现实，二者分别卡在 P1-6 和 P1-4。
2. 真实成本被低估：真评测 3–4 周、KQ-2 完整版 2–3 周、A1 隔离 2 周。
3. 做完用户也不感知：埋点、评测、治理元数据、缺口看板（若没有知识运营每天看）都不可感知；只有反馈按钮和引用片段可感知。
4. 先还信任债：任意登录用户可读任意会话；评测随机分；Sessions 页「近 24 小时 6,508」硬编码（`Sessions.tsx:56`）；Knowledge 上传弹窗仍写「演示环境不真正保存」（`Knowledge.tsx:340`）。
5. 无答案的双向错误：哈希向量 + 0.8 阈值会漏召回，关键词路又硬塞低质上下文；硬拒答会误伤，必须先有可答/不可答金标。
6. 反馈偏差：只有不满的人点踩；反馈覆盖率低于 10% 时不要解读采纳率。

### 1.2 技术架构视角

**一句话判断：闭环可落地，但现状只有「会话 / 消息 / 引用 JSON」三块半成品**，反馈、事件、缺口、真实评测、文档治理均缺表缺接口。最大风险不是编码量，而是检索置信度当前不可判定，以及工作区未提交的检索重构会造成接口冲突。

**现状盘点（均有代码依据）**

- 会话：`chat_sessions` 无 user_id/title/updated_at/deleted_at（`V1__init.sql:67-81`）；`SessionApplicationService.list()` 先 `findAll()` 再内存过滤，按 `HH:mm:ss` 字符串倒序，跨天会错；管理端删除仅 ADMIN，消息读接口无归属校验。
- 消息：`session_messages` 无 answer_status/拒答原因/检索元数据；助手消息 ID 不回传前端（KQ-1 改动已补，见 §2.3）；SSE 错误/中止不落助手消息，会话会卡 `running`。
- 引用：`MessageCitation` 只有四字段；`RetrievalHit` 有 chunkIndex/score/text 但无 chunkId；`documents.version` 有，但无 updated_at，版本历史是按版本号伪造的。
- 评测：`EvalApplicationService.run` 随机 92–97 分；`eval_runs` 只有展示字段；门禁按名称匹配并用展示时间字符串排序。
- 事件：V1–V10 无 feedback/event 表；`usage_records` 存在但全代码无写入；`DomainEvent` 接口未被使用。
- 检索置信度：阈值默认 0.8，只过滤向量分；关键词路不过滤；展示分是 rerank > 原始向量 > RRF 的回退值，RRF 只有 1/61~2/61 量级，不是置信度；rerank 默认关闭；未配 Embedding Key 时用哈希向量，绝对值不可跨环境比较。
- 文档治理：dataset 有 owner；document 无 owner/生效/过期；`document_chunks.metadata_json` 列存在但仓储写入 null。

**技术方案（模块 / 迁移 / 估算）**

| 项 | 归属 | 迁移 | 规模 |
|---|---|---|---|
| A1 会话管理 | chat + portal | V11 加 user_id/title/updated_at/deleted_at，索引 `(user_id,updated_at)` | M，3–5 天 |
| B0 埋点 | observe（事件用 common 事件解耦，避免 chat→observe 循环依赖） | V13 `product_events`（event_id 幂等） | M，2–4 天 |
| KQ-1 反馈与拒答 | chat | V12 `message_feedback` + 消息状态/检索诊断 | L，5–8 天 |
| KQ-2 缺口看板 | observe v1 | V14 `knowledge_gaps` + `knowledge_gap_items` | L，5–8 天 |
| KQ-3 引用可信化 | knowledge + chat | V15 documents 治理字段 + 可选版本快照 | M/L，5–8 天 |
| KQ-4 真实评测 | eval + chat + knowledge | V16 eval_cases/eval_run_cases，扩展 eval_runs | XL，10–15 天 |
| KQ-5 文档治理 | knowledge | V15 治理字段 | M，4–6 天 |

合计 30–45 人日，单人 6–9 周，必须并行。

**低置信与无答案判定（推荐）**

不建议依赖单一 `threshold`。在检索服务输出 `RetrievalConfidence`：无命中直接拒答；向量路 top1 ≥ answerT 则答，≤ refuseT 则拒，中间带用一次小模型证据判定（temperature=0、JSON `{sufficient,reason}`、3 秒超时、失败则带「低置信」声明作答）；hybrid 有 rerank 时用 rerank top1，否则用向量 top1；纯 keyword 没有绝对置信度，只能用关键词覆盖率 + LLM 判定，**不能拿 RRF 分数当置信度**。阈值按 dataset + embedding 模型分别用金标集校准，目标误拒率 <5%。哈希向量环境应关闭硬拒答或只标 LOW_CONFIDENCE。

**评测可复现性**

执行器异步化（202 + runId，独立线程池，`SELECT ... FOR UPDATE SKIP LOCKED` 或 locked_until 防重复）；评分器两层：检索指标用 chunkId 算 Recall@10/nDCG@10/MRR，答案质量用 LLM judge + 「是否应拒答/是否带引用」规则。可复现的关键是冻结变量：`config_json` 记录 embedding 模型/维度、chunkSize/overlap、retrievalMode/topK/threshold、rerank 模型；`corpus_fingerprint` 记录文档 id+version+checksum；judge 模型、rubric 版本、seed 一并存。没有语料指纹，同一评测集在变动的知识库上跑两次不可比。门禁绑定 `eval_run_id`，校验 run 状态、用例数下限、指标阈值、相对基线不回退、run 新鲜度与语料指纹一致。

**架构风险**

1. Modulith 边界：observe 已依赖 chat，chat 不能再直接调 observe 写事件；用 common 事件 + Spring 事件解耦；portal 调 chat 会话服务需补 `@NamedInterface`。
2. Flyway 演进：`eval_runs.time_label`、`documents.updated`、`chat_sessions.time_label` 都是展示字符串，真实时间要新增列并回填；`session_messages.created_at` 秒精度，同秒消息排序会抖。
3. SSE 改造：`done` 加字段风险低；但错误/中止不落助手消息，反馈会挂空；`turns` 读改写并发会丢更新；rerank 15 秒超时在热路径，建议降到 2–3 秒并加熔断。
4. 埋点写入压力：所有非 GET 已被 `AuditLogFilter` 同步记审计，再叠加事件会放大写量；必须 after-commit、异步批量、幂等键、失败不阻断。
5. 环境失真：哈希向量 + Milvus/ES 关闭时，KQ-4 的分数测的是回退链路，不能代表生产。
6. 未提交改动冲突：工作区已大改 `KnowledgeRetrievalService`（rerank/MMR）、`RetrievalHit`、端口迁到 `domain/service`；KQ-3/KQ-5 会碰同一文件与 `docs/backlog.md`。
7. 归属与隐私：历史会话无法回填；反馈 comment 可能含 PII；当前任何登录用户可按 sessionId 读任意会话。

**两周最小切片**

只做 **A1 + B0 + KQ-1** 的垂直切片：用户能看会话列表、重命名/删除；助手消息返回 messageId；👍/👎/没解决+原因落库；无命中/低置信拒答落库并在管理端可查；事件记录会话漏斗。若有余量，做 KQ-3 薄版（引用带 chunkId，点开 chunk 上下文抽屉）和 KQ-2 只读列表。KQ-4 完全不动。

依赖顺序：`A1 → done 带 messageId/answerStatus → KQ-1 → B0（并行）→ KQ-5 元数据 → KQ-3 → KQ-2 → KQ-4`。产品文档的「KQ-4/5 并行」只在把 KQ-5 的分块/文档元数据提前到 KQ-3 之前时成立；否则 KQ-3 会重做 chunk 模型。

### 1.3 测试视角

**一句话判断：当前证据不足以证明闭环成立。** 后端只有 3 个测试方法，检索金标 3 条，评测与门禁是演示逻辑，反馈/埋点/用户归属均无数据模型。闭环要成立，必须先补「可归属、可复现、可对账」三件事。

**测试现状**

- 后端 2 个测试类、3 个 `@Test`：`ApplicationContextTests`（contextLoads、TypeHandler 回读）与 `ModularityTests`（模块边界）。无 Controller/安全/SSE/检索/评测/工作流/文件/并发测试。
- `pom.xml` 仅 starter-test、modulith-test、H2；无 spring-security-test、Testcontainers、WireMock、Awaitility、JaCoCo。H2 会执行 V1–V10，但方言/锁/索引行为与 MySQL 有差异。
- 两个前端 `package.json` 只有 dev/build/preview，无测试脚本；`npm run build` 仅 `tsc --noEmit + vite build`。
- 金标 `retrieval-golden.example.json` 仅 3 条，全正样本，无无答案/难负样本；`retrieval_eval.py` 只算 Recall/MRR/nDCG（二值），无基线、无 JSON 输出、恒 exit 0。
- `.github/workflows/ci.yml` 在 PR/main 会跑 `mvn test` + 双前端 build，但只覆盖上述 3 个测试方法，等于没有有效回归；当前工作区的未提交改动未经 CI 验证。
- 无回归保护的关键链路：SSE runtime、检索融合/MMR/Rerank、会话读取与越权、引用持久化、发布门禁、文档版本/下载、评测执行。

**逐项验证设计（要点）**

- **KQ-1 反馈/拒答**：反馈按 `message_id+user_id` 唯一；拒答原因/分数/命中数落库；他人消息反馈 403/404；同用户重复提交更新不新增；阈值 0.79/0.81 边界在 SSE `done` 中表现为 no_answer 与正常；未登录 401、非法 reason 400。
- **KQ-2 缺口**：三类来源入池、归一化去重、指派关闭可追溯；补文档 reindex 后同问题命中并正反馈才允许关闭；viewer 403；重复跑聚合任务不重复计数。
- **KQ-3 引用**：引用字段完整且只含实际使用的 chunk；文档换版后旧引用标失效，文件缺失 410；下载越权 403、随机 ID 404；同文档多 chunk 去重口径固定。
- **KQ-4 评测**：固定 commit/索引快照/评测集、temperature=0+seed、固定 judge 版本；连跑两次 per-case 完全一致；bad case 一键入集；低分/无评测/超期 → approve 409；并发触发只允许一个 running。
- **KQ-5 治理**：过期文档仍检索但标 expired 并显示「已过期」，不静默丢弃；分块预览含 index/token/section/page；存量字段为空按长期有效；V11 在 H2/MySQL 均通过。
- **埋点**：事件字典、服务端落库、properties 白名单无 PII、eventId 重试幂等、写失败不阻断对话、每日与会话/消息表对账差异 ≤1%。
- **会话管理**：A 列表不含 B 会话；A 读/删 B 资源 403/404；删除级联 messages/citations/feedback；断线重连不重复落库；localStorage 旧快照迁移不重复。

**AI 质量评估方法**

- 评测集 100–300 条起步：正样本约 60%，难负样本 25%（同领域但实体/数字/版本不符、已过期政策），边界 15%（部分可答、多跳、阈值附近）。每条标注 answerable、gold doc/chunk、参考答案、是否应拒答、难度；双人标注 + 仲裁，κ≥0.8；test 集冻结，调阈值只用 dev。
- 指标：可答召回、误拒率、误答/幻觉率、引用精确率/召回率、答案正确率、groundedness。阈值在 dev 上画 PR 曲线选点，在 test 固定点报告。
- LLM-as-judge：固定模型与 prompt 版本，temperature=0+seed，同题 3 次取多数/中位数；pairwise 交换 A/B 位置消除位置偏差；judge 必须看到问题、检索上下文、参考答案，输出结构化 rubric；用 50–100 条人工标注校准，报告一致率/κ；每次评测设 token 预算上限。

**回归与门禁**

- PR 快速档（<10 分钟）：domain 单测、H2 集成、接口 401/403/200、SSE 契约（stub 模型网关 + 固定流）、前端 build；不连真实模型/Milvus/ES。
- 夜间档：Testcontainers MySQL、固定快照金标检索、E2E smoke（登录→提问→引用→反馈→会话列表）、1k/10k/100k chunks 性能基线。
- 发布门禁：候选 commit + 固定索引快照跑核心 30–50 条 + 检索指标（Recall@10 不低于基线 -1pt）+ 误拒率上限 + 权限矩阵；门禁记录不可变，超 7 天或 commit 不符即失效。
- 不稳定用例 quarantine，仅基础设施类可重试 1 次，质量断言不重试；flaky 率 >2% 阻断合并。

**发布前风险清单**

1. 会话越权（双账号资源矩阵、审计抽查）。
2. SSE 并发/断连丢轮次、上游未释放（并发压测、模型调用数 vs 请求数）。
3. 引用/文档不一致，替换、删除、回滚后旧引用不可核验（v1→v2→回滚、IDOR、citations 对账）。
4. 拒答误伤（灰度影子统计各数据集拒答率、人工抽检、A/B 看负反馈）。
5. DB 回退检索 O(n) 性能（10k/100k chunks 压测、堆内存/GC）。
6. 评测成本与不稳定（单次 token/费用上限、重复运行方差）。
7. 埋点/反馈丢失重复（每日对账、eventId 唯一约束）。
8. 门禁绕过（状态机路径用例、时间戳/commit 校验）。

---

## 2. 交叉核对

### 2.1 三方强共识

1. **会话归属/越权是上线阻断项。** 产品称其为「闭环被身份卡住」，架构指出管理端消息接口无归属校验且 JWT 已有 uid 但后端从未使用，测试把它列为 T1/T5 级安全用例。三方都认为不能只保护反馈接口。
2. **无答案判定不能等同于「检索结果为空」。** 关键词路无阈值（`KnowledgeRetrievalService.java:114-122`），RRF 分数不是置信度；三方分别给出「软提示先行」「置信度分层 + LLM 判定」「可答/不可答金标 + 误拒率上限」的互补方案。
3. **反馈必须挂消息 ID，`done` 必须回传。** 这一点当前 KQ-1 改动已经实现（`AgentRuntimeService.java:207-210,283-285`），与三方要求一致。
4. **真实评测（KQ-4）不是切片，应后置。** 产品「砍出本轮」，架构「放最后」，测试给出「不做的代价是改进无法证明」——结论一致但测试补上了验收债。
5. **埋点与反馈必须同期。** 没有 B0，KQ-2 缺口看板、有效会话率、复盘都没有数据源；产品给出了事件字典与漏斗，迭代 01 设计给出了 9 个事件与写入可靠性方案。
6. **测试基础必须补。** 3 个测试方法、3 条金标不足以支撑任何「闭环成立」的结论；新链路要自带单测/集成/E2E，CI 必须真的跑。
7. **引用可信化先做「片段」，页码/章节后置。** 产品拆 a/b，架构建议 chunk 上下文抽屉，测试要求换版失效可验——三者共同指向 KQ-3a（chunk 级引用 + 上下文抽屉），页码级锚点依赖 P1-4。

### 2.2 分歧与待仲裁

| 议题 | 产品 | 架构 | 测试 | 仲裁建议 |
|---|---|---|---|---|
| 本轮范围 | 4–6 周 MVP（反馈+埋点+缺口 Lite+引用片段） | 2 周 A1+B0+KQ-1 | 先补归属+SSE 契约+测试骨架 | 以 KQ-1 为 M1，紧接 B0/B2；A1 完整版下轮，但越权修复本轮 |
| KQ-4 位置 | 砍出本轮 | 最后做 | 必须有真实评测才能证明变好 | 后置，但本轮先补金标与 bad case 导出，为 KQ-4 铺路 |
| 归属字段 | 先有身份再谈会话管理 | user_id 全隔离 | 跨用户读/写全部 403/404 | 统一为一个字段；鉴于代码已用 `created_by`，本轮以它为唯一归属，P1-8 再迁 user_id |
| 拒答策略 | HYBRID 软提示、STRICT 硬拒答 | 置信度分层 + LLM 证据判定 | 需误拒率上限与金标 | 采纳 PRD 的 HYBRID/STRICT 设计，但 STRICT 只允许绑定向量/混合数据集；补软提示 |
| 埋点口径 | 有效会话需重新定义 | 事件异步化 + 幂等 | 事件要对账、可重放 | 采用迭代 01 B0 的 9 个事件，并给 `answer_completed` 增加检索诊断字段 |

### 2.3 已抽查核实的事实

| 事实 | 依据 |
|---|---|
| `chat_sessions` 无 user_id/created_by/title/updated_at/deleted_at | `backend/src/main/resources/db/migration/V1__init.sql:67-81` |
| 管理端会话列表/详情/消息接口只要求登录，无属主校验；仅删除有 `@PreAuthorize` | `.../chat/interfaces/rest/SessionController.java:44,78,90,104-107` |
| 全局规则仅 `anyRequest().authenticated()`，无路径级角色限制 | `.../framework/config/SecurityConfig.java:133-138` |
| user-web 直接调用管理端 `/sessions/{id}/messages` | `user-web/src/api.ts:112` |
| 向量检索按 `dataset.threshold()` 过滤，关键词检索路不过滤 | `.../knowledge/application/query/KnowledgeRetrievalService.java:106,114-122` |
| 引用按 documentId 去重，chunkIndex/score 被丢弃；`MessageCitation` 仅四字段 | `.../chat/application/runtime/AgentRuntimeService.java:151-166`；`.../chat/domain/model/MessageCitation.java:14` |
| 评测分数为 `92 + Math.random()*5` | `.../eval/application/service/EvalApplicationService.java:58` |
| 后端仅 2 个测试类、3 个 `@Test` | `backend/src/test/java/.../ApplicationContextTests.java`、`ModularityTests.java` |
| 检索金标仅 3 条查询，且全为正样本 | `docs/retrieval-golden.example.json`（`queries` 长度 3） |
| CI 会执行 `mvn test` 与双前端 build | `.github/workflows/ci.yml:25-56` |
| `usage_records` 无任何业务写入代码 | `grep UsageRecord backend/src/main/java` 无结果 |
| KQ-1 改动已让 `done` 返回 `assistantMessageId/answerType/lowConfidence` | `AgentRuntimeService.java:207-210,283-285` |
| 当前工作区 V11 = `created_by` + `answer_mode` + `answer_type/low_confidence` + `message_feedback` | `backend/src/main/resources/db/migration/V11__answer_feedback.sql` |
| 迭代 01 设计文档的 V11 = `user_id` + 软删 + `product_events` + 反馈 | `docs/iteration-01/02-technical-design.md:180-201` |
| 三份文档的原因标签与备注长度不一致 | `01:61,124`；`02:675-684`；`PRD:111,113`；迁移 `comment VARCHAR(200)` |
| 规划文档、迭代文档、V11 迁移均未提交 | `git status`：`?? docs/product-iteration.md`、`?? docs/iteration-01/`、`?? docs/prd/`、`?? V11__answer_feedback.sql` |

### 2.4 两条方案线的冲突面

1. **迁移编号冲突**：两边都叫 V11，但内容不同。若按迭代 01 设计继续写，只能改成 V12，且 `chat_sessions` 会同时存在 `created_by` 与 `user_id` 两个归属字段。
2. **反馈模型冲突**：迭代 01 B1 要求处理状态（open/handled/ignored）、500 字备注、六类通用标签；KQ-1 迁移是 200 字、六类知识问答标签、有 `follow_up_requested`、无处理状态。
3. **会话管理冲突**：迭代 01 A1 要求完整列表/重命名/软删/跨设备；KQ-1 只加 `created_by` 且明确不修读接口越权（PRD §5.2「已知残留风险」）。
4. **洞察数据源冲突**：迭代 01 B2 依赖 `product_events`；KQ-1 只有反馈列表与统计，没有事件表，无法算有效会话与漏斗。

---

## 3. 修订建议

### 3.1 范围与里程碑

**推荐把两条线合并成一条，按「先安全、再反馈、后洞察」推进：**

| 里程碑 | 内容 | 对应 | 估算 |
|---|---|---|---|
| M0 收口 | 统一 V11、统一标签/备注长度、更新 01/02 文档、提交 RAG 基线 | — | 0.5 天 |
| M1 安全归属 | `created_by` 写入 + 读接口属主校验（消息/详情/列表）+ 管理端角色限制 + 历史无主会话仅管理员可见 | KQ-1 提前项 | 1 天 |
| M2 反馈闭环 | 反馈表/接口/前端反馈条/原因弹层/状态恢复/管理端列表与统计 | KQ-1 | 3 天 |
| M3 回答模式 | `answer_mode` 配置、STRICT 拒答、HYBRID 低置信提示、拒答落库 | KQ-1 | 2 天 |
| M4 埋点基线 | `product_events` + 9 个事件 + 异步幂等写入 + 有效会话口径 | B0 | 2.5 天 |
| M5 会话管理 | 列表、重命名、软删、跨设备续聊（复用 `created_by`） | A1 | 3 天 |
| M6 洞察只读 | 反馈/漏斗/未解决/高频问题页 | B2 | 2 天 |
| M7 验收 | 测试矩阵、迁移演练、构建、发布清单 | — | 1.5 天 |

合计约 15.5 人日，含缓冲 3 周。若只能两周，砍 M5/M6，只交付 M0–M4；A1/B2 进入下一迭代。**不建议按原迭代 01 的 13.5 人日同时铺 A1+B0+B1+B2，因为反馈与回答模式的设计还没统一。**

### 3.2 数据与迁移

- **V11 只保留一份**：以当前 `V11__answer_feedback.sql` 为基础，补齐本轮必要的会话字段（`title`、`updated_at`、`deleted_at`）与反馈处理状态（`handle_status`），或明确这些放 V12。
- `product_events`、`documents` 治理字段、`knowledge_gaps` 依次使用 V12/V13/V14，**不要复用 V11 编号**；更新 `02-technical-design.md` 的迁移编号。
- 归属字段二选一：如果本轮不做完整 A1，继续用 `created_by`，并在文档中写明「P1-8 迁移到 user_id」；不要再加 `user_id` 制造双字段。
- 原因标签统一为 PRD 的六类（`NOT_RELEVANT/INCOMPLETE/OUTDATED/WRONG_SOURCE/NO_SOURCE/OTHER`），是否保留「不安全或冒犯」由产品决定；三份文档同步。
- 备注长度统一为 200 字（与迁移一致），删除文档中的 500 字写法。
- 历史无主会话（`created_by IS NULL`）：管理端可见，用户端不可见、不可反馈；**不要**「任意登录用户放行」，那会把越权从读扩大到写。

### 3.3 无答案与拒答

- 保留 HYBRID（默认，软提示 + `low_confidence`）与 STRICT（硬拒答）两种模式，与 KQ-1 PRD 一致。
- **STRICT 模式只允许绑定向量或混合检索数据集**；纯关键词数据集不允许选 STRICT，或在 UI 明示「该模式对关键词检索无效」。否则 `hits` 永远非空，拒答形同虚设。
- 在 `session_messages.retrieval` 中持久化 `hitCount/topScore/mode/noHit`，供 KQ-2 与复盘使用；不要把这些塞进事件 properties 的唯一副本。
- 误拒监控：上线两周内按智能体看拒答率，>30% 触发阈值校准（KQ-4），不要临时放宽规则。
- 哈希向量环境（未配置真实 Embedding Key）只用于演示，报告中必须标注「该环境的分不代表生产」。

### 3.4 埋点与指标

- 先落迭代 01 B0 的 9 个事件；在 `answer_completed` 上增加检索诊断字段（`hit_count/top_score/mode/no_hit/rerank_used`）。
- 事件写入：after-commit、异步批量、`event_id` 幂等、失败不阻断、每日对账；`AuditLogFilter` 已同步记审计，注意写放大。
- 有效会话口径按产品修订：至少一条「有引用或明确拒答」且未被 👎/重问推翻；同时保留旧口径做对比，避免指标断崖无法解释。
- 原文只存消息表；事件 properties 白名单，禁止原文/PII。

### 3.5 测试与门禁

**本轮最低测试要求（合入前必须通过）：**

1. 单测：反馈 rating/reason/comment 校验；拒答策略（STRICT 0 命中 → REFUSAL 且模型调用 0 次）；HYBRID 0 命中 → `low_confidence=true`。
2. 集成：反馈 upsert 唯一约束；他人消息反馈 404/403；会话消息/详情/列表属主校验；`done` 字段完整性；V11 在 H2 与 MySQL 上迁移成功。
3. E2E smoke：A/B 隔离；提问→反馈→刷新恢复；拒答卡片→留下问题；401/403/400。
4. 回归：现有 3 个测试 + 新增测试全部进 CI；PR 必须跑 `mvn test` + 双前端 build。
5. 测试数据：两个普通账号、一个开发者账号；3 智能体 × 5 会话 × 20 消息 × 10 反馈；3 条敏感样本。

**下一迭代补：** 金标集扩到 50–100 条（含不可答与难负样本）、SSE 契约测试、Testcontainers MySQL、E2E 自动化、发布门禁绑定真实评测。

---

## 4. 评审会必须回答的问题

1. **本轮到底以哪条方案线为准？** KQ-1（6.5 人日）还是迭代 01 全量（13.5 人日）？两者能否合并为「M1 安全 + M2 反馈 + M3 回答模式」，其余顺延？
2. **会话读接口越权本轮修不修？** 产品/PRD 暂时接受残留风险；架构与测试均认为这是上线阻断项。修复成本约 1 天。
3. **归属字段用 `created_by` 还是 `user_id`？** 只能选一个，避免双字段与迁移冲突。
4. **STRICT 模式是否限制数据集检索类型？** 纯关键词数据集下拒答失效，是否接受？
5. **反馈标签与备注长度以哪份文档为准？** 建议 PRD 六类 + 200 字。
6. **B0/B2 是否跟随本轮？** 若不跟随，缺口看板、有效会话率与复盘都缺数据；若跟随，需接受 3 周排期。

---

## 5. 待决策清单（建议默认值）

| # | 决策 | 建议默认 |
|---|---|---|
| 1 | 方案线 | KQ-1 为 M1，B0/B2 紧随；A1 完整版下轮，越权修复本轮 |
| 2 | 归属字段 | 统一 `created_by`，P1-8 再迁 `user_id` |
| 3 | 越权修复 | 本轮修：消息/详情/列表均校验属主，历史无主仅管理员可见 |
| 4 | STRICT 范围 | 仅向量/混合数据集可选；纯关键词禁用并提示 |
| 5 | 标签/备注 | PRD 六类，备注 200 字；三份文档同步 |
| 6 | 评测 | KQ-4 单独立项；本轮只扩金标与 bad case 导出 |

> 本文档为独立评审输入，不修改任何既有需求/设计结论；采纳后请回写 `01-requirements-review.md`、`02-technical-design.md` 与 `docs/prd/KQ-1-*`，并在 `docs/backlog.md` 登记迁移编号与测试缺口。
