---
name: backend-security
description: 修改 Agent Platform 后端认证、JWT/OIDC、RBAC、审计、密钥或权限边界时使用；覆盖接口授权、角色、Redis 限流和注销黑名单。
---

# 后端安全开发

## 先读

- `backend/src/main/java/com/agentplatform/framework/config/SecurityConfig.java`
- `modules/system/application/auth/AuthService.java`
- `modules/system/infrastructure/security/AuditLogFilter.java`
- `framework/security/{TokenBlacklistService,LoginRateLimiter,JwtBlacklistValidator}.java`
- `frontend/src/App.tsx`、`frontend/src/api.ts`

## 认证

- 本地 JWT：`POST /api/v1/auth/login`。
- OIDC：配置 `OIDC_ISSUER_URI`，Resource Server 同时接受 OIDC 与本地 JWT。
- Token 必须包含 `sub`、`roles`、`jti`、`exp`。
- 登出写 Redis 黑名单；不要在客户端“只删除 localStorage”就算注销。

## 授权

角色：

```text
PLATFORM_ADMIN
DEVELOPER
EVALUATOR
VIEWER
```

规则：

- 查询默认 `authenticated()`。
- 写操作至少 `PLATFORM_ADMIN` 或 `DEVELOPER`。
- 成员、密钥、审计、删除等敏感操作只给 `PLATFORM_ADMIN`。
- 不要把权限判断写在 Controller 的 `if` 里；用 `@PreAuthorize`。

## 审计

- `AuditLogFilter` 自动记录所有非 GET `/api/v1/**` 写操作。
- 登录成功/失败、退出在 `AuthService`/`AuthController` 显式记录。
- 不要记录密码、完整 API Key、MCP Token 或 Cookie。
- 审计写入失败不能阻断业务请求。

## 密钥

- DeepSeek/Embedding/MCP/COS 密钥通过环境变量或 KMS/SSM 注入。
- 不要把密钥写进 `application.yml`、前端、日志或 Git。
- 本地 `.env` 必须 `chmod 600` 且被 `.gitignore` 忽略。

## 新增受保护接口

```java
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
@PostMapping
public ApiResponse<X> create(...) { ... }
```

更新 SecurityConfig 白名单时必须确认接口不返回敏感数据；不要为了图方便打开 `/api/v1/**`。

## 验收

- 未登录 401。
- 角色不足 403，且响应体使用 `FORBIDDEN`。
- Token 注销后 401。
- 登录失败 5 次后 15 分钟内 403。
- 审计表有本次操作记录。
