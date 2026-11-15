---
name: backend-security
description: 修改 Agent Platform 认证、JWT/OIDC、RBAC、审计、Redis 限流/黑名单、Provider/MCP 密钥加密或 CORS 时使用。
---

# 安全开发

## 先读

- `framework/config/SecurityConfig.java`
- `framework/security/{SecretCipher,TokenBlacklistService,LoginRateLimiter,JwtBlacklistValidator}.java`
- `modules/system/application/auth/AuthService.java`
- `modules/system/infrastructure/security/AuditLogFilter.java`
- `common/error/ErrorCode.java`
- `modules/model/infrastructure/persistence/entity/ProviderEntity.java`
- `modules/tool/infrastructure/persistence/entity/McpServerEntity.java`

## 认证模型

- 无状态 Spring Security Resource Server，不使用 Session。
- 本地登录：`POST /api/v1/auth/login`，BCrypt 校验后签发 HS256 JWT。
- OIDC：配置 `OIDC_ISSUER_URI` 后，JwtDecoder 优先按 OIDC 校验，失败回退本地 JWT。
- JWT 声明：
  - `sub`：用户 ID/用户名
  - `roles`：平台角色
  - `jti`：Token 唯一 ID，用于注销黑名单
- 登出：`POST /api/v1/auth/logout` 把 `jti` 写入 Redis 黑名单；`JwtBlacklistValidator` 拦截已注销 Token。
- 登录限流：Redis 记录失败次数，默认 5 次 / 15 分钟。
- 密码只存 BCrypt 哈希，禁止明文/可逆加密。

## 角色

| 角色 | 典型权限 |
|---|---|
| `PLATFORM_ADMIN` | 成员、API Key、删除、模型厂商、全局配置 |
| `DEVELOPER` | 智能体、知识库、工具、工作流、模型配置 |
| `EVALUATOR` | 评测集、评测运行、发布门禁相关 |
| `VIEWER` | 只读 |

- 写接口必须加 `@PreAuthorize`。
- 后端是最终权限边界；前端隐藏按钮只是体验。
- 用户门户 `/api/v1/user/*` 只要求登录，并通过 `USABLE = {running, grayscale}` 白名单限制智能体。

## 白名单

`SecurityConfig` 放行：

```text
/actuator/health/**
/actuator/info
/v3/api-docs/**
/swagger-ui/**
/swagger-ui.html
/api/v1/auth/login
/api/v1/auth/config
/api/v1/system/info
OPTIONS /**
```

新增公开接口前必须确认无敏感数据；其余默认要求认证。

## 审计

`AuditLogFilter` 记录写操作：

- 方法、路径、状态码、操作人、IP、时间。
- 不记录密码、Token、Cookie、请求体中的密钥、完整提示词。
- 登录/登出单独记录。
- 审计写入失败不应影响主请求（以现有实现为准）。

新增敏感写操作时确认会被过滤器覆盖；必要时在应用服务补充业务审计。

## 密钥与敏感数据

### Provider API Key

- 落库前经 `SecretCipher`（AES-GCM）加密到 `api_key_encrypted`。
- 对外只返回掩码 `key` 和 `keyConfigured`，`apiKey()` 必须 `@JsonIgnore`。
- 更新密钥接口只接收新 Key，不回显明文。
- 日志/异常中不能出现 Key。
- `SECRETS_KEY` 变更会导致历史密钥无法解密，必须重新录入。

### MCP Token

- `McpServerEntity` 使用同一 `SecretCipher`。
- API 视图不返回 Token；只能更新/覆盖。
- 内部服务地址受 `MCP_ALLOW_PRIVATE_URLS` 控制。

### 其他

- `.env` 权限 600，已被 `.gitignore` 忽略。
- 不提交 `DEEPSEEK_API_KEY`、`SECRETS_KEY`、JWT Secret、DB 密码。
- 生产使用腾讯云密钥管理/环境变量，不把密钥写进镜像。

## CORS

- `app.cors.allowed-origins` 逗号分隔。
- 允许携带凭证、Authorization。
- 生产必须配置为实际域名，不要使用 `*` + credentials。

## 常见安全缺口

- API Key 管理目前只有创建/列表，尚无外部 API Key 认证过滤器。
- 多租户/组织隔离未实现。
- 工作流/知识库没有细粒度数据权限。
- 生产 TKE 的 Secret/密钥轮换未完全落地。

## 新增受保护接口

```java
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','DEVELOPER')")
@PostMapping
public ApiResponse<X> create(@Valid @RequestBody XRequest request) { ... }
```

测试：

- 无 Token → 401。
- VIEWER/错误角色 → 403。
- 正确角色 → 200。
- 登出后旧 Token → 401。
- 连续错误登录 → 限流。

## Checklist

- [ ] 写接口有 @PreAuthorize。
- [ ] 返回 DTO 不含明文密钥/密文。
- [ ] 审计覆盖且不记录敏感字段。
- [ ] Token 不写日志。
- [ ] CORS 生产域名受限。
- [ ] 新增公开接口经过评审。
- [ ] 401/403/200 验证。
- [ ] 中文注释说明安全约束。
