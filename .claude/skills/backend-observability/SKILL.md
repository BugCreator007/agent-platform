---
name: backend-observability
description: 修改运营概览、会话分析、成本统计、Micrometer 指标、Prometheus、OTLP tracing、告警规则或企微通知时使用。
---

# 可观测性开发

## 先读

- `modules/chat/application/query/{SessionAnalyticsService,SessionAnalyticsPort,SessionFact,AgentSessionStat}.java`
- `modules/chat/infrastructure/persistence/repository/MybatisSessionAnalyticsAdapter.java`
- `modules/observe/application/service/{OverviewApplicationService,AlertRuleService,AlertApplicationService,CostApplicationService}.java`
- `modules/observe/interfaces/rest/{ObserveController,AlertRuleController}.java`
- `admin-web/src/pages/{Overview,Alerts,Cost}.tsx`
- `backend/src/main/resources/application.yml` 的 management/tracing 配置

## 运营概览

`OverviewApplicationService` 基于真实 `chat_sessions` 计算：

- 今日会话、成功率、P95 首响、成本估算。
- 实时脉搏：运行会话、在线智能体、错误率、P99、吞吐。
- 趋势：sessions/tools/success/p95/cost。
- 快照时间、rangeDays。
- 智能体调用分布。
- 告警与待发布。

口径：

- 时区固定 `Asia/Shanghai`。
- 成功状态 `success`；失败集合 `failed/timeout/error`。
- 在线智能体 `running/grayscale`。
- 成本估算：会话 tokens × 模型输出单价 / 1,000,000（元）。
- 价格来自 `ModelCatalog` 与智能体绑定模型。

## 会话分析

读模型：

```text
SessionAnalyticsPort.sessionsBetween(from,to)
MybatisSessionAnalyticsAdapter 查 chat_sessions
SessionAnalyticsService 聚合 agentStats
```

- 只加载轻量 `SessionFact`，不等同加载 trace 大字段。
- 成功率只统计终态会话，running 不进分母。
- P95 用内存排序计算。
- 时间窗口非法返回空列表。

## 成本

当前：

- `OverviewApplicationService` 的成本是基于 tokens 的估算。
- `CostApplicationService.summary()` 仍是演示静态数据，预算存在内存，重启恢复。
- Provider 的 calls/used 未按真实调用累加。

待补：真实 token 计费、模型单价、usage 流水、按团队/智能体聚合、预算硬限制。

## 指标

Agent 运行时：

- `agent_runtime_requests_total{agent}`
- `agent_runtime_errors_total{agent}`
- `agent_runtime_duration{agent}`

配置：

- Actuator `health,info,metrics,prometheus`
- Micrometer Prometheus registry
- OTLP tracing endpoint `OTEL_EXPORTER_OTLP_ENDPOINT`
- Logstash encoder

新增指标：

```java
meters.counter("xxx_total", "tag", value).increment();
meters.timer("xxx_duration", "tag", value).record(ms, MILLISECONDS);
```

## 告警

`AlertRuleService`：

- `@Scheduled(fixedDelayString="${app.alert.evaluate-interval-ms:60000}")`
- 规则 metric：`error_rate`、`p95_latency`、`budget_used`、`quota_used`。
- 超过阈值生成 Alert，按 rule+title 去重 active。
- 企微 Webhook 通知，未配置只记录日志。
- `quota_used` 暂无数据源，固定 0。
- `p95_latency` 当前用 timer.mean 近似，不是精确 P95。

API：

```text
GET  /api/v1/alerts
POST /api/v1/alerts/{id}/ack
POST /api/v1/alerts/{id}/resolve
GET  /api/v1/alert-rules
POST /api/v1/alert-rules
PUT  /api/v1/alert-rules/{id}
GET  /api/v1/cost/summary
PUT  /api/v1/cost/budget
GET  /api/v1/overview?rangeDays=7
```

## 前端

- Overview 使用真实 overview 数据，展示 KPI/脉搏/趋势/分布。
- Alerts 支持规则开关/新增、告警 ack/resolve。
- Cost 仍部分演示，前端不要把静态数据当真实计费。
- 深色/浅色主题必须都检查。

## 新增指标/告警

1. 在产生点埋点（Agent/Workflow/Tool）。
2. 确认指标名、tag 基数可控（不要用 sessionId 等高基数 tag）。
3. 在 AlertRuleService 添加 metricValue 分支。
4. 规则/阈值可通过页面配置。
5. 验证 Prometheus `/actuator/prometheus`。
6. 通知失败不影响主流程。
7. 添加中文注释。

## 测试

- `/actuator/health` UP。
- `/actuator/prometheus` 有指标。
- `/api/v1/overview` 数据不报错。
- 告警规则触发后生成 Alert。
- 企微 Webhook 可选验证。
- `admin-web` 的 Overview/Alerts/Cost 页面可加载。

## 当前限制

- 成本静态、预算内存。
- 无真实 P95/直方图、无 quota 数据。
- 仅企微通知，无邮件/短信/飞书。
- 无告警静默/升级/值班。
- OTLP 需要外部 collector；本地 4318 未启动会刷错误日志，可接受但应配置。
- TKE/腾讯云 CLS/APM 未联调。

## Checklist

- [ ] 指标 tag 低基数。
- [ ] 统计时区/状态口径一致。
- [ ] 前端展示与真实数据一致。
- [ ] 告警去重、确认、恢复。
- [ ] 不记录敏感信息。
- [ ] 中文注释。
