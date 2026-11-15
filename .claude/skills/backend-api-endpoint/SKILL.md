---
name: backend-api-endpoint
description: 新增或修改 Agent Platform REST/SSE 接口时使用；覆盖 ApiResponse、ErrorCode、DTO 校验、Controller、权限、OpenAPI、上传下载、SSE 和前端契约同步。
---

# REST / SSE 接口开发

## 先读

- 目标模块 `interfaces/rest` 现有 Controller。
- `common/api/ApiResponse.java`
- `common/error/ErrorCode.java`、`BusinessException.java`
- `framework/web/GlobalExceptionHandler.java`
- `backend/src/main/java/com/agentplatform/framework/config/SecurityConfig.java`
- 对应前端 `admin-web/src/api.ts` / `user-web/src/api.ts`

## 统一响应

所有普通接口返回：

```java
ApiResponse.ok(data)
ApiResponse.error(code, message)
```

结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": {},
  "timestamp": "2026-01-01T00:00:00Z"
}
```

- 业务失败抛 `new BusinessException(ErrorCode.XXX, "提示")`。
- 不要手写 `ResponseEntity` 包装业务错误；全局处理器统一映射。
- 错误码：`VALIDATION_ERROR(400)`、`UNAUTHENTICATED(401)`、`FORBIDDEN(403)`、`NOT_FOUND(404)`、`NAME_CONFLICT(409)`、`STATE_CONFLICT(409)`、`INTERNAL_ERROR(500)`。

## Controller 模板

```java
/**
 * 智能体管理接口。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
    private final AgentApplicationService service;

    public AgentController(AgentApplicationService service) { this.service = service; }

    /**
     * 查询智能体列表。
     *
     * @param status 可选状态过滤
     * @param q      可选关键词
     * @return 智能体列表
     */
    @GetMapping
    public ApiResponse<List<AgentSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String q) {
        return ApiResponse.ok(service.list(status, q));
    }

    /**
     * 创建智能体。
     *
     * @param request 创建参数
     * @return 新智能体
     */
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
    @PostMapping
    public ApiResponse<AgentSummary> create(@Valid @RequestBody CreateAgentRequest request) {
        return ApiResponse.ok(service.create(request));
    }
}
```

## DTO 与校验

- 请求 DTO 放 `interfaces/dto`，使用 record + `jakarta.validation`。
- `@NotBlank`、`@Size`、`@Min`、`@Valid`。
- 不用 `Map<String,Object>` 承接复杂请求；简单开关/动作接口可以接受 Map，但要集中解析和校验。
- 返回 DTO 不要直接暴露领域对象中的密钥、加密密文、内部字段。
- null 字段用空串/空数组或明确 nullable，避免 `Map.of` NPE。

## 权限

- 写接口必须 `@PreAuthorize`。
- 常见角色：
  - `PLATFORM_ADMIN`：成员、密钥、删除、全局配置。
  - `DEVELOPER`：智能体、知识库、工具、工作流、模型配置。
  - `EVALUATOR`：评测相关。
  - `VIEWER`：只读。
- 用户门户 `/api/v1/user/*` 只要求登录，并通过状态白名单控制可访问智能体。
- Controller 不加 `@PreAuthorize` 时仍受 SecurityConfig `anyRequest().authenticated()` 约束。

## SSE 接口

```java
@PostMapping(value = "/sessions:stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter stream(@RequestBody RuntimeChatRequest request) {
    return runtime.stream(request);
}
```

要求：

- 返回 `SseEmitter`，设置超时（当前 180s）。
- 事件名 `meta/delta/tool/tool-result/done/error`。
- 发送失败要 `completeWithError`；客户端断开/超时要释放上游订阅。
- 不要用原生 `EventSource` 带 Authorization，前端使用 `fetch + ReadableStream`。
- 事件数据约定见 `backend-agent-runtime`。

## 文件上传/下载

- multipart 用 `@RequestPart("file") MultipartFile`，`consumes = MULTIPART_FORM_DATA_VALUE`。
- 文件大小由 `spring.servlet.multipart.max-file-size/request-size` 控制。
- 下载通过 `Content-Disposition`，SecurityConfig 已暴露该响应头。
- 存储路径/对象键不要暴露本地绝对路径。

## API 路径约定

- 管理端：`/api/v1/<resource>`。
- 用户端：`/api/v1/user/<resource>`。
- 动作：`POST /resource/{id}/action` 或 `POST /resource/{id}:action`。
- 查询参数：`status`、`q`、`page`、`size`。
- 不在 URL 中传密钥、Token、Cookie。

## OpenAPI

- 保持 springdoc 可达：`/swagger-ui.html`、`/v3/api-docs`。
- DTO 字段命名与前端一致；不暴露敏感字段。
- 新增 Controller 后确认 Swagger 能正常生成。

## 前端契约同步

管理端：

```ts
api.get<Agent[]>('/agents')
api.post<Agent>('/agents', body)
api.put<Agent>(`/agents/${id}`, body)
api.del<void>(`/agents/${id}`)
```

用户端：

- `/user/agents`
- `/user/chat:stream`
- `/sessions/{id}/messages`

字段改名/类型变化必须同步 `admin-web/src/api.ts`、`user-web/src/api.ts` 和页面，`tsc --noEmit` 会强制检查。

## 测试与验收

- 401：无 Token。
- 403：有 Token 但角色不足。
- 200：合法请求。
- 400：参数校验失败。
- 404：资源不存在。
- 409：名称/状态冲突。
- SSE：确认 `meta`、`delta`、`done`，异常时 `error`。
- `mvn test`；前端 `npm run build`。

## 常见错误

- 在 Controller 写业务规则/事务/SQL。
- 忘记 `@Valid`，非法字段进入 application。
- 返回领域对象导致密钥泄露。
- `Map.of` 遇到 null 直接 500。
- SSE 在请求线程里同步 `block`。
- 修改了接口但没同步前端类型。
- 只在前端隐藏按钮，后端没有 `@PreAuthorize`。
