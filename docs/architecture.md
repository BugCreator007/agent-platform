# 工程结构说明

## 后端单模块 + DDD 包

```text
com.agentplatform
├── AgentPlatformApplication
├── common                 # 共享内核：ApiResponse、ErrorCode、DomainEvent
├── framework              # 技术配置：Security、OpenAPI、异常处理
├── admin                  # 启动与系统接口
└── modules
    ├── agent
    │   ├── interfaces/rest
    │   ├── application
    │   ├── domain/model
    │   ├── domain/repository
    │   └── infrastructure
    ├── chat
    ├── knowledge
    ├── model
    ├── release
    ├── eval
    ├── observe
    ├── open
    ├── system
    ├── tool
    └── workflow
```

## 依赖规则

- `interfaces` → `application` → `domain`
- `infrastructure` 实现 `domain` 端口
- `domain` 不依赖 Spring/MyBatis/HTTP
- 模块之间只调用 application API 或领域事件
- `common` 是共享内核；`framework` 是技术框架

## 设计文档

完整 DDD 设计、腾讯云部署、前后端契约见上级目录的
`Agent平台管理后台-技术设计文档-v2.md`。
