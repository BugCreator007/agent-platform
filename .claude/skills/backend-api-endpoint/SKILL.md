---
name: backend-api-endpoint
description: 在 Agent Platform 新增或修改 REST 接口时使用；覆盖 DTO 校验、Controller、ApiResponse、权限、错误码、OpenAPI 和前端 API 契约同步。
---

# 后端 REST 接口开发

## 先读

- `backend/src/main/java/com/agentplatform/common/api/ApiResponse.java`
- `backend/src/main/java/com/agentplatform/common/error/{BusinessException,ErrorCode}.java`
- `backend/src/main/java/com/agentplatform/framework/web/GlobalExceptionHandler.java`
- 目标模块的 `interfaces/rest` 和 `application/service`
- `frontend/src/api.ts` 和实际调用页面

## 统一响应

所有接口返回 `ApiResponse<T>`：

```java
return ApiResponse.ok(applicationService.list());
```

失败抛 `BusinessException`：

```java
throw new BusinessException(ErrorCode.NOT_FOUND, "智能体不存在: " + id);
```

不要手写 `ResponseEntity<Map>` 作为成功响应；异常由 `GlobalExceptionHandler` 统一映射。

## 新增接口步骤

1. 在 `interfaces/dto` 建 record 请求体，使用 `jakarta.validation`：

```java
public record CreateXRequest(
        @NotBlank @Size(max = 120) String name,
        String owner
) {}
```

2. 在 application service 实现业务动作，Controller 只做协议转换。
3. Controller 使用 `@RestController`、`@RequestMapping`、`@Valid`。
4. 写操作加权限：

```java
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
@PostMapping
public ApiResponse<XSummary> create(@Valid @RequestBody CreateXRequest request) {
    return ApiResponse.ok(service.create(request));
}
```

5. 查询接口默认要求已认证；公开接口必须显式加入 `SecurityConfig` 白名单并确认无敏感数据。
6. 写操作审计由 `AuditLogFilter` 自动记录；不要在 Controller 重复手写审计。
7. 更新 OpenAPI：全局 Bearer 已配置；DTO 字段加 `@Schema(description=...)` 可选。
8. 同步前端契约：

- `frontend/src/api.ts` 增加/修改 TypeScript interface。
- 对应页面 mutation/query 更新。
- 运行 `npm run build`。

## 错误码

使用现有 `ErrorCode`：

- `VALIDATION_ERROR` 400
- `UNAUTHENTICATED` 401
- `FORBIDDEN` 403
- `NOT_FOUND` 404
- `NAME_CONFLICT` 409
- `STATE_CONFLICT` 409
- `INTERNAL_ERROR` 500

不要给客户端返回堆栈、SQL 或密钥。

## 验证

```bash
cd backend && mvn test
cd frontend && npm run build
```

至少用 curl 或前端验证：未登录 401、无权限 403、正常 200、参数错误 400。
