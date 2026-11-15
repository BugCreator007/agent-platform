---
name: backend-persistence
description: 修改 Agent Platform 的 MySQL/Flyway/MyBatis-Plus 持久化时使用；覆盖迁移版本、Entity、Mapper XML、TypeHandler、仓储分层、事务、种子数据和 H2 测试。
---

# 持久化开发（MyBatis-Plus）

## 当前技术栈

- MySQL 8 生产；H2 MySQL 模式测试。
- Flyway 管理表结构；`spring.flyway.locations=classpath:db/migration`。
- MyBatis-Plus 3.x；`mapper-locations=classpath*:mapper/*.xml`。
- 不再使用 JPA/Hibernate；不要把 `@Entity`、`ddl-auto` 带回来。

## 目录约定

```text
infrastructure/persistence/
  entity/XxxEntity.java              @TableName 实体
  mapper/XxxMapper.java              BaseMapper + 自定义方法
  repository/XxxRepository.java      实体仓储，包装 Mapper
  repository/MybatisXxxRepository.java 领域端口适配器
backend/src/main/resources/mapper/
  XxxMapper.xml                      自定义 SQL
```

- 单表 CRUD 用 `BaseMapper`；多条件/聚合/联表写 XML。
- Entity 只做数据库映射；Domain 业务规则放 `domain/model`。
- 领域端口在 `domain/repository`；应用层只依赖端口。

## Flyway 迁移

当前迁移到 `V9`：

```text
V1__init.sql
V2__alert_rules.sql
V3__agent_model_binding_and_messages.sql
V4__document_source.sql
V5__mcp_servers.sql
V6__mcp_server_enabled.sql
V7__model_provider_routing.sql
V8__workflow_runtime_state.sql
V9__session_analytics_index.sql
```

规则：

- 已应用的文件禁止修改；新增 `V10__description.sql`。
- 同一版本号只能有一个文件；并行开发时先检查 `ls db/migration`。
- MySQL/H2 都要兼容；避免 MySQL 专有 JSON 函数、`INSERT IGNORE`、复杂 `ON DUPLICATE KEY`。
- 迁移必须幂等可重跑（本地清库后能从 V1 到最新）。
- 时间字段统一 `TIMESTAMP` / `Instant`，生产连接使用 `connectionTimeZone=UTC` 或明确 `serverTimezone=Asia/Shanghai`。

示例：

```sql
ALTER TABLE providers ADD COLUMN protocol VARCHAR(32) NOT NULL DEFAULT 'openai';
ALTER TABLE providers ADD COLUMN api_key_encrypted TEXT;
CREATE INDEX idx_sessions_created ON chat_sessions (created_at);
```

## Entity 模板

```java
/**
 * 智能体持久化实体，对应 agents 表。
 */
@TableName(value = "agents", autoResultMap = true)
public class AgentEntity {
    @TableId(type = IdType.INPUT) private String id;
    @TableField(value = "provider_id") private String providerId;
    @TableField(typeHandler = StringListTypeHandler.class) private List<String> tools;
    @TableField(value = "created_at") private Instant createdAt;

    public static AgentEntity from(Agent agent) { ... }
    public Agent toDomain() { ... }
}
```

关键点：

- `@TableId(type = IdType.INPUT)`：ID 由应用生成。
- `@TableName(autoResultMap = true)`：TypeHandler 查询回读必需。
- 字段名与迁移一致；snake_case 与 camelCase 映射由配置处理，但显式 `@TableField` 更安全。
- `FieldStrategy.ALWAYS` / `NOT_NULL` 根据更新语义设置；全局默认 `always`。
- Entity 不要写业务方法。

## TypeHandler

- `StringListTypeHandler`：角色、工具、知识库、能力标签等字符串列表。
- `TraceStepListTypeHandler`：会话 trace 复杂对象。
- `WorkflowNodeListTypeHandler`：流程节点 JSON。
- 新复杂字段：新增模块内 TypeHandler，放在 `infrastructure/persistence`。
- 列表字段 Entity 必须 `autoResultMap = true`，否则查询读不回。

## Mapper 与 XML

Mapper：

```java
public interface AgentMapper extends BaseMapper<AgentEntity> {
    List<AgentEntity> findByStatus(@Param("status") String status);
}
```

XML 放 `resources/mapper/AgentMapper.xml`：

```xml
<select id="findByStatus" resultType="...AgentEntity">
  SELECT * FROM agents WHERE status = #{status}
</select>
```

- 自定义 SQL 用 `#{}`，禁止字符串拼接用户输入。
- 聚合查询返回 Entity 或专用 Row 类型；不要返回 Map 给应用层。
- XML 中列名与 ResultMap/TypeHandler 对齐。

## 仓储分层

1. 实体仓储：`AgentRepository` 注入 `AgentMapper`，返回 Entity。
2. 领域适配器：`MybatisAgentRepository implements domain.AgentRepository`，注入一个或多个实体仓储，做转换。
3. 多表聚合：如 `MybatisWorkflowInstanceRepository` 聚合实例仓储 + 节点运行仓储。
4. 应用服务事务：涉及多个仓储的写操作用 `@Transactional`。

## 事务

- 事务边界在 application service，不在 Controller。
- 多表写、删除级联、种子初始化要考虑事务。
- 只读查询不需要事务。
- 失败补偿/外部调用不要放在长事务里。

## 种子数据

- `*DataSeeder implements ApplicationRunner`，`@ConditionalOnProperty(app.demo.seed)`。
- 首次启动空表时插入；新增内置数据可用“缺失才插入”的增量策略。
- 不覆盖用户修改；删除用户数据前必须确认。
- 关联数据顺序用 `@Order`。

## 测试与手动检查

```bash
cd backend && mvn test
```

测试使用 H2 + Flyway，必须覆盖：

- 上下文启动。
- 列表/JSON TypeHandler 写后读。
- 新表/字段迁移。
- 关键查询。

手动检查：

```bash
docker exec -e MYSQL_PWD=agent123 agent-mysql-local \
  mysql -uagent agent_platform -e "SHOW TABLES; SELECT COUNT(*) FROM agents;"
```

## 常见错误

- 两个 `V8__*.sql` 并行提交 → Flyway duplicate version。
- 改 Entity 忘 XML/ResultMap。
- 列表字段读回 null：缺 `autoResultMap=true`。
- `Map.of` 不接受 null。
- MySQL/H2 类型差异导致测试失败。
- 应用层直接注入 Mapper，绕过领域端口。
- 在事务中调用外部 HTTP，导致长事务/连接占用。
