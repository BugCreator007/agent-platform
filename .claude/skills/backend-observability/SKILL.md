---
name: backend-observability
description: 修改指标、Prometheus、OpenTelemetry、告警规则、企微通知、成本或健康检查时使用。
---

# 可观测性与告警开发

## 先读

- `backend/src/main/resources/application.yml` 的 management 配置。
- `AgentRuntimeService` 的 Micrometer counter/timer。
- `modules/observe/application/service/AlertRuleService.java`
- `deploy/k8s/` 健康检查。
- `deploy/docker-compose.yml` 的 ES/Milvus。

## 指标

- 运行时请求：`agent_runtime_requests_total`（tag: agent）。
- 运行失败：`agent_runtime_errors_total`。
- 耗时：`agent_runtime_duration`。
- 工作流、摄取、MCP 调用新增指标时使用低基数 tag（agent、provider、tool），不要用用户 ID/会话 ID 作为 tag。

## 健康检查

- `/actuator/health/readiness`
- `/actuator/health/liveness`
- `/actuator/prometheus`

## 告警

告警规则表 `alert_rules`：

- `metric`、`threshold_value`、`level`、`channel`、`enabled`。
- `AlertRuleService.evaluate()` 定时评估，活跃告警去重。
- 企微 Webhook：`app.notify.wecom-webhook`。
- 通知失败不能影响业务主流程。

## 日志与追踪

- 不记录密码、Token、Cookie、完整提示词和工具凭据。
- OTLP endpoint：`OTEL_EXPORTER_OTLP_ENDPOINT`。
- 生产建议接 CLS 日志、TMP 监控、APM 链路。
- 本地没有 OTLP collector 时出现 exporter 连接失败是预期告警，不影响请求。

## 验证

```bash
curl -s http://localhost:8081/actuator/health
curl -s http://localhost:8081/actuator/prometheus | head
```

创建一条低阈值告警规则，等待评估，检查 `/alerts` 和企微通知；不要在生产用测试告警长期开启。
