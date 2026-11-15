---
name: backend-tool-execution
description: 修改工具目录、ToolExecutionService、内置/HTTP 工具、工具健康检查、weather.query 等工具执行逻辑时使用；MCP 协议本身另见 backend-mcp-integration。
---

# 工具执行开发

## 先读

- `modules/tool/domain/model/Tool.java`
- `modules/tool/domain/repository/ToolRepository.java`
- `modules/tool/application/service/{ToolApplicationService,ToolExecutionService}.java`
- `modules/tool/infrastructure/persistence/{ToolSeedData,ToolDataSeeder}.java`
- `modules/tool/interfaces/rest/ToolController.java`
- `admin-web/src/pages/Tools.tsx`

## 工具模型

```text
Tool(name, type, owner, calls, errorRate, health, checked, enabled, description, endpoint)
```

- `type`：`API`、`内置`、`MCP`。
- `endpoint`：HTTP 工具的 POST 地址；内置/MCP 可为空。
- `enabled`：停用工具调用会直接失败。
- `health/checked`：健康检查展示。
- 工具种子在 `ToolSeedData`；已有库用 `ToolDataSeeder` 增量补充。

## 执行入口

```java
String execute(String name, String input)
```

流程：

1. `tools.findByName(name)`，不存在抛 `IllegalArgumentException`。
2. `enabled=false` 抛 `IllegalStateException`。
3. `type=MCP` → `McpRuntimeService.execute(name, {input})`。
4. `endpoint` 非空 → POST `{"input": "..."}`，20 秒超时。
5. 否则进入内置 switch：
   - order.query
   - risk.score
   - kb.search
   - refund.apply
   - bi.query
   - notify.send
   - weather.query
   - default simulated

## 内置工具

### weather.query

- 输入支持 JSON：`city`、`address`、`query`、`text`；也支持纯文本。
- 会去掉“天气…”和问号后缀。
- 调用 `https://wttr.in/{city}?format=j1&lang=zh`（GET）。
- 返回 JSON：city/temperature/feelsLike/weather/humidity/windSpeed/observationTime/source。
- 失败抛 `天气接口调用失败`，由工作流节点重试/补偿。
- 该工具用于「上海天气查询」流程的真实接口分支。

### 其他

- `order.query`、`risk.score` 等是本地演示返回，便于工作流/Agent 演示。
- `notify.send` 只是工具模拟，真实通知走 `WorkflowNotificationService`。

## Agent 运行时的限制

- Agent 的工具调用只把 **MCP 工具**暴露给模型 function calling。
- 普通 HTTP/内置工具目前只做本地关键词预取（如 order.query），不会自动生成 function schema。
- 如果希望智能体调用 `weather.query`，需要先扩展 Agent 工具循环，或通过工作流工具封装。

## 新增内置工具

1. 在 `ToolSeedData` 添加工具（并更新 `ToolDataSeeder` 增量插入策略）。
2. 在 `ToolExecutionService` switch 增加分支或独立方法。
3. 明确输入格式、输出 JSON、超时、异常语义。
4. 若作为工作流节点使用，填写 `config.tool`。
5. 更新 `admin-web` 工具列表/说明（如需要）。
6. 用真实 HTTP 或 mock 服务验证。
7. 添加中文注释。

## 新增 HTTP 工具

- 现有 HTTP 工具约定 `POST endpoint`，body `{"input": input}`。
- 外部服务必须支持该协议；否则新增专用方法。
- 设置连接/响应超时，捕获异常转业务错误。
- 生产需域名白名单/SSRF 防护；当前 Tool 服务没有独立 URL 校验。

## 健康检查

- `ToolApplicationService.checkAll()` 更新 checked/health。
- 当前健康检查是本地状态更新，不是真实端点探测。
- 如需真实健康检查，应对每个 endpoint 发请求并记录延迟/错误。

## 安全

- 工具输出视为不可信，Agent 提示词中必须提醒防止提示词注入。
- 不在日志中输出完整请求/响应中的敏感字段。
- 不允许用户直接指定任意 URL 作为工具 endpoint（需要白名单/审批）。
- 写操作工具需要人工审批（当前尚未实现）。

## 验证

```bash
TOKEN=...
curl -X POST http://localhost:8081/api/v1/mcp-tools:call ... # MCP 使用
```

内置工具可通过工作流节点或 `ToolExecutionService` 调用验证；weather.query 需要外网。

## Checklist

- [ ] 工具已注册且 enabled。
- [ ] 输入/输出契约明确。
- [ ] 超时、异常、降级。
- [ ] 敏感输出不落日志。
- [ ] 工作流/Agent 使用方式一致。
- [ ] 前端工具列表可见。
- [ ] 中文注释。
