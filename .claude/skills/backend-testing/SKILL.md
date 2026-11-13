---
name: backend-testing
description: 为 Agent Platform 后端增加或修改单元测试、Spring 上下文测试、Modulith 边界测试、接口测试和前端契约构建验证时使用。
---

# 后端测试与验证

## 现有测试

- `backend/src/test/java/com/agentplatform/ModularityTests.java`：模块边界。
- `backend/src/test/java/com/agentplatform/ApplicationContextTests.java`：H2 + Flyway 全上下文加载。
- `backend/src/test/resources/application-test.yml`：测试数据源。

## 必跑

```bash
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn test

cd ../frontend
npm run build
```

## 测试分层

- 领域单元测试：不启动 Spring，验证状态机和业务不变量。
- 应用服务测试：Mock repository 或 H2。
- 接口测试：`@SpringBootTest`/MockMvc，带 JWT 和角色。
- Modulith：`ApplicationModules.of(...).verify()`，跨模块依赖必须通过 `@NamedInterface`。
- E2E：登录 → 创建/编辑 → 调用运行时/工作流 → 删除。

## 使用 H2

测试 profile 已启用 H2 MySQL 模式和 Flyway。新增迁移必须同时兼容 MySQL 与 H2：
- 避免 MySQL 特有函数。
- 不使用 `JSON` 类型，使用 `TEXT/LONGTEXT`。
- 自增主键使用 `BIGINT AUTO_INCREMENT`。

## 外部依赖

- 单元/上下文测试不要依赖真实 MySQL、Redis、Milvus、ES。
- 集成测试使用 Testcontainers 或显式 profile；不要默认启动外部服务。
- DeepSeek 测试使用 mock/fallback，不要把 Key 写进测试。

## 失败排查

- `Schema-validation: missing table`：Flyway 未执行或迁移被 baseline 跳过。
- `TransactionRequiredException`：derived delete 缺 `@Transactional`。
- Modulith `non-exposed type`：增加 `@NamedInterface` 或调整依赖方向，不要把 infrastructure 暴露。
- `Map.of` NPE：检查 null 的 providerId/modelId。

## 验证清单

- [ ] `mvn test` 通过
- [ ] `npm run build` 通过
- [ ] 关键接口 401/403/200
- [ ] 新增迁移在 H2 和 MySQL 都执行
- [ ] 没有提交 `.env`、密钥、日志中的敏感信息
