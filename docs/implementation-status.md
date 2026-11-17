# 实现状态

> 未完成事项与生产化缺口见 [backlog.md](backlog.md)。

## 已完成

### 持久化
- MySQL 8 + Flyway（`V1__init.sql`、`V2__alert_rules.sql`）。
- MyBatis-Plus 实体与 Mapper 适配器覆盖智能体、会话、数据集/文档/分块、模型/厂商/路由、发布、评测、工具、流程/实例/节点执行、告警/规则、成员/密钥/审计、用户。
- 原内存仓储已重构为仅供首次启动使用的种子数据类，不再作为运行时仓储。
- 生产默认连接 MySQL；测试使用 H2 MySQL 模式，Flyway 同样执行。

### 安全
- JWT 登录、OIDC Resource Server 兼容（`OIDC_ISSUER_URI`）。
- RBAC：平台管理员、开发者、评测员、只读成员；写接口加 `@PreAuthorize`。
- 会话归属：`chat_sessions.created_by` 记录登录名；会话列表/详情/消息/续聊/反馈统一校验属主，历史无主会话仅管理员/开发者可见；用户端历史消息改走 `/api/v1/user/sessions/{id}/messages`。
- Redis 登录失败限流（5 次 / 15 分钟）与 JWT 登出黑名单。
- 审计日志覆盖登录、退出和所有写操作（方法、路径、状态、操作人、IP）。
- 前端登录页、Bearer Token 注入、401 自动退出、顶部退出登录。
- 会话调试页、会话消息持久化、历史会话回看；智能体与模型厂商/模型绑定。

### Agent 运行时
- 多模型路由：OpenAI-compatible（DeepSeek、Kimi、千问、OpenAI、智谱）与 Anthropic Messages。
- 智能体按 `providerId + modelId` 绑定真实模型，按路由策略执行主模型失败转移。
- Provider 密钥使用 AES-GCM 加密入库，连通性测试会真实请求厂商；未配置任何 Key 时提供本地演示流。
- Agent 运行时注入提示词、工具、知识库上下文，SSE 输出。
- 知识库检索结果与工具调用结果进入系统提示词。
- 运行时会话与调用链写入数据库。
- 前端智能体沙盒调试接入真实 SSE。

### 知识库
- multipart 文件上传，本地/COS 存储。
- PDF、DOCX、Markdown、TXT、HTML 解析；按段落/长度切分与重叠。
- Embedding（OpenAI-compatible；未配置时确定性哈希向量）。
- Milvus 向量写入/检索；Elasticsearch 关键词索引；DB 回退混合检索 + RRF。
- 文档替换、版本、回滚、异步索引、重建索引、检索测试、智能体授权。
- 知识分块与向量持久化。

### 反馈、拒答与埋点
- 消息级反馈：👍/👎、六类点踩原因、备注 ≤200 字、幂等 upsert/撤销；管理端分页筛选、质量汇总、脱敏与处理状态扩展位。
- 回答模式：HYBRID 低置信提示；STRICT 仅允许向量/混合数据集，0 命中不调用模型直接拒答；助手消息落检索诊断 `retrieval_json`。
- 拒答卡片：用户端展示固定文案，支持「留下问题」转 `DOWN + NO_SOURCE` 反馈。
- B0 埋点：V12 `product_events`，服务端 6 类事件 + 客户端 3 类事件；after-commit 异步写入、`event_id` 幂等、失败降级；管理端事件统计。
- 隐私：反馈列表手机号/身份证/银行卡掩码；事件不记原文；客户端事件白名单与限流。

### 工作流
- 流程实例、节点执行记录、运行状态和分支选择持久化。
- 图执行：START/KNOWLEDGE/MODEL/TOOL/CONDITION/APPROVAL/NOTIFY/TIMER/SUBFLOW/JOIN/END。
- 并行 fan-out、JOIN 汇聚、SpEL 条件路由。
- TIMER 与 SUBFLOW 进入等待态，由常驻恢复调度器继续执行。
- 节点补偿工具按逆序执行；NOTIFY 支持企微与通用 Webhook 真实发送。
- 节点失败重试、错误记录、成功/失败统计。
- 人工审批等待、审批恢复、取消。
- 可选定时触发调度器。
- 前端运行实例列表、节点执行记录、审批/取消、节点配置。

### 运维与发布
- Prometheus 指标、OpenTelemetry OTLP tracing 配置、运行时/工作流指标。
- 告警规则持久化、定时评估、活跃告警去重、企微 Webhook 通知。
- 发布审批评测门禁（最低分可配置）。
- Dockerfile、Docker Compose、TKE Deployment/Service/Ingress/HPA、CI 示例。

## 仍是演示/待完善

- DeepSeek 可以使用环境变量 `DEEPSEEK_API_KEY`，也可以在模型厂商页面配置；Kimi、千问、Anthropic、OpenAI、智谱需要在页面配置各自的 Key 后才会真实调用。
- Embedding 未配置远程模型时使用哈希向量，语义效果有限；生产建议配置 bge-m3 等模型。
- Milvus/Elasticsearch 默认关闭；Docker Compose 已提供，生产需配置实际地址与凭证。
- 工作流已支持并行分支、汇聚、条件路由、子流程、补偿和定时器；跨进程分布式锁与大规模任务队列仍需生产化。
- 工具执行支持 HTTP 与 MCP；MCP OAuth、resources/prompts、写工具人工审批和周期性健康检查仍未实现。
- 评测门禁已接入，但评测执行目前是演示评分；真实评测集用例执行与评分器仍需扩展。
- 告警指标目前来自 Micrometer 与成本汇总，更多业务指标需要补充埋点。
- V11–V13 已在 H2 与本地 MySQL 存量库验证；真实 Redis 限流压力、admin-web 浏览器 E2E 尚未演练。
- 反馈 `handle_status` 目前只落库/展示，处理动作属于 B2；事件保留期清理任务未实现。
- 腾讯云中间件（CDB、Redis、TDMQ、COS、ES、Milvus、CLS/APM）配置已预留，未在真实云环境联调。
- 前端单 chunk 约 596 KB，尚未做路由级代码分割。
- 无完整 E2E、性能和安全渗透测试。

## 本地运行

```bash
cd /Users/lu/面试/agent-platform
docker compose -f deploy/docker-compose.yml up -d
```

或使用本地 MySQL/Redis：

```bash
cd backend && mvn spring-boot:run
cd admin-web && npm run dev
```

演示账号：`admin/admin123`、`developer/developer123`、`viewer/viewer123`。
