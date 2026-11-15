---
name: backend-persistence
description: 为 Agent Platform 后端新增或修改 MySQL/MyBatis-Plus 持久化时使用；覆盖 Flyway 迁移、Entity、Mapper、XML、Repository、事务与种子数据。
---

# 后端持久化开发

## 先读

- `backend/src/main/resources/db/migration/`：现有 V1–V4。
- `backend/src/main/resources/application.yml`：MySQL、MyBatis-Plus、Flyway。
- 目标模块的 `domain/repository`、`infrastructure/persistence`。

## 关键原则

- 默认数据库是 MySQL 8；测试使用 H2 MySQL 模式。
- 不再使用 Hibernate DDL 校验：表结构只由 Flyway 建，Entity 的 `@TableName`/`@TableField` 必须与迁移一致。
- 运行时不得使用内存仓储；`*SeedData` 只用于首次启动初始化。
- 已应用的迁移文件不要修改，新增 `V{n}__description.sql`。

## 新增表 / 字段

1. 新增迁移：

```sql
ALTER TABLE agents ADD COLUMN provider_id VARCHAR(64);
ALTER TABLE agents ADD COLUMN model_id VARCHAR(128);
CREATE INDEX idx_agents_provider ON agents (provider_id);
```

2. 更新 MyBatis-Plus Entity（字段访问模式）：

```java
@TableField("provider_id")
private String providerId;
```

3. 更新 `toDomain()` / `from(domain)` 映射。
4. 如果字段可能为空，注意 `Map.of` 不接受 null；API 返回前做空值转换。
5. 更新领域模型构造器时保留旧构造器重载，避免影响种子数据和历史调用。

## 新增仓储

领域端口只声明业务方法：

```java
public interface XRepository {
    Optional<X> findById(String id);
    List<X> findAll();
    void save(X value);
    void deleteById(String id);
}
```

基础设施分层：

```java
// persistence/repository/XRepository.java：包装 Mapper
@Repository
public class XRepository {
    private final XMapper mapper;
    public List<XEntity> findAll() { return mapper.selectList(null); }
    ...
}

// 多表端口适配器聚合多个 Repository
@Primary
@Repository
public class MybatisXRepository implements XRepository {
    private final ARowRepository rows;
    ...
}
```

- 命名：`XEntity`、`XMapper`、`XMapper.xml`、`XRepository`，按实体名统一。
- Mapper 放 `persistence/mapper`，XML 放 `resources/mapper`，Repository 放 `persistence/repository`。
- 自定义 SQL 写在 XML；单表 CRUD 用 BaseMapper。
- 写操作涉及多表时在适配器或应用服务加 `@Transactional`。

## 列表字段

短字符串列表使用 `StringListTypeHandler`；复杂对象（TraceStep、WorkflowNode）使用模块内 JSON TypeHandler。实体必须加 `@TableName(autoResultMap = true)`，否则查询时 TypeHandler 不生效。

## 种子数据

- 每个模块一个 `*DataSeeder implements ApplicationRunner`。
- 只在目标表为空时插入。
- 有关联顺序时使用 `@Order`（例如知识库先数据集、后分块）。
- 不要覆盖用户数据。

## 验证

```bash
cd backend && mvn test
```

并直接检查 MySQL：

```bash
docker exec agent-mysql mysql -uagent -pagent123 agent_platform -e "SHOW TABLES; SELECT COUNT(*) FROM <table>;"
```
