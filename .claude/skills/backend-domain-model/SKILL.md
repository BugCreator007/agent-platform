---
name: backend-domain-model
description: 设计或修改 Agent Platform 的聚合根、实体、值对象、状态机、领域不变量和领域仓储端口时使用；适用于 Agent、Workflow、Release、Provider、Dataset 等模型。
---

# 领域模型开发

## 原则

- 领域层是业务规则的唯一归属地；Controller、Application Service、Entity 不重复实现状态规则。
- 领域对象不依赖 Spring、MyBatis、Jackson、HTTP。
- 聚合根负责维护内部一致性；外部只能通过方法修改状态。
- 领域仓储端口只描述业务需要，不暴露分页/SQL 细节。
- 查询 DTO 放在 `application/query`，不要用领域对象直接当接口响应。

## 模型类型选择

- **聚合根/可变成状态对象**：普通 class，如 `Agent`、`WorkflowInstance`、`Release`、`Provider`。
- **只读查询结果**：record，如 `AgentSummary`、`ModelRoute`、`UserAgentView`、`RetrievalHit`。
- **值对象/枚举**：`AgentStatus`、`ToolCall`、`WorkflowNode`。
- **持久化实体**：只放 infrastructure，不参与业务规则。

## 状态机模式

以 `WorkflowInstance` 为例：

```text
RUNNING → WAITING_APPROVAL | WAITING_TIMER | WAITING_SUBFLOW → RUNNING
       → SUCCEEDED | FAILED | CANCELLED
```

要求：

- 每个状态迁移封装成方法：`running()`、`waitApproval()`、`waitTimer()`、`waitSubflow()`、`succeed()`、`fail()`、`cancel()`。
- 非法迁移抛 `IllegalStateException` 或由应用层转换为 `BusinessException(STATE_CONFLICT)`。
- 状态方法同时维护时间字段、错误字段、恢复时间等派生状态。
- 应用层负责持久化，domain 不调用 repository。

以 `Agent` 为例：

- 草稿、运行、灰度、暂停的迁移由 `publish()`、`pause()`、`resume()` 实现。
- `bindModel(providerId, modelId, modelName)` 维护模型绑定。
- 状态变化不应由 Controller 直接 set。

## 构造函数与兼容性

- 新增字段时保留旧构造器重载，避免破坏种子数据、历史调用和测试。
- 旧构造器可以委托给完整构造器，提供默认值。
- 字段可空时明确默认值（空列表、空字符串、null 的语义）。
- ID 由应用层生成，domain 只接收并校验非空。

示例：

```java
/** 兼容旧调用方的构造器。 */
public WorkflowInstance(...旧参数...) {
    this(...旧参数..., null, null);
}

/** 完整构造器。 */
public WorkflowInstance(...旧参数..., String stateJson, Instant resumeAt) { ... }
```

## 集合与 null

- 领域对象对外返回 `List.copyOf(...)` / `Map.copyOf(...)`，防止外部修改内部状态。
- `Map.of` 不接受 null；向 API 返回前把 null 转为空串或 null-safe 结构。
- 查询记录字段允许 null 时在 DTO 中明确，不要用 `Map.of` 拼装。

## 领域方法示例

```java
/**
 * 绑定智能体使用的模型。
 *
 * @param providerId 厂商 ID
 * @param modelId    模型 ID
 * @param modelName  展示名称
 */
public void bindModel(String providerId, String modelId, String modelName) {
    this.providerId = providerId;
    this.modelId = modelId;
    if (modelName != null && !modelName.isBlank()) this.model = modelName;
}
```

## 仓储端口

```java
public interface WorkflowRepository {
    List<Workflow> findAll();
    Optional<Workflow> findById(String id);
    void save(Workflow workflow);
}
```

- 方法以业务语言命名，不出现 `selectList`、`insert`。
- 复杂查询可以单独建 query port（如 `SessionAnalyticsPort`），放在 application/query 或 domain/repository。
- 多表组合由 infrastructure 适配器完成。

## 新增字段 Checklist

- [ ] 领域字段 + 读取方法。
- [ ] 兼容旧构造器。
- [ ] 状态/不变量校验。
- [ ] Entity 字段与 `@TableField`。
- [ ] Entity ↔ Domain 转换。
- [ ] 迁移 SQL。
- [ ] Query DTO 与前端类型。
- [ ] 种子数据默认值。
- [ ] 上下文测试能读写。
- [ ] 中文注释。

## 常见错误

- 在 Controller 里判断 `if (status == ...)`。
- 领域对象直接依赖 MyBatis Entity。
- 新增字段只改 Entity，忘了 Domain 转换。
- 用 `Map.of` 包装可能为 null 的模型名。
- 状态方法不更新时间/错误字段。
- 让 domain 调用 repository 或 Spring 事务。
