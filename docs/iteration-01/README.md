# 迭代 01 文档索引

> 迭代主题：KQ-1 回答反馈与无答案识别 + B0 产品埋点基线（含会话读接口越权修复）
> 周期：2026-11-17 起，预计 2 周
> 当前阶段：KQ-1 + B0 实现与复审修正完成（2026-11-18，见 [07](07-implementation-report.md)），待复评；MySQL 存量迁移与 user-web 浏览器 E2E 已通过，admin-web E2E 与限流压测未做

## 文档

| 文档 | 内容 | 状态 |
|---|---|---|
| [01-requirements-review.md](01-requirements-review.md) | 需求评审记录：范围、现状核对、开发提问与产品答复、验收标准 | 历史版本（A1/B2 已顺延） |
| [02-technical-design.md](02-technical-design.md) | 技术设计：架构、数据、接口、埋点、权限、测试、排期 | 历史版本（迁移编号与范围以 05 为准） |
| [03-technical-review.md](03-technical-review.md) | 技术评审通知、议程、检查单、产品/测试重点、结论记录表 | 通知与材料就绪，会议待开 |
| [04-three-lens-independent-review.md](04-three-lens-independent-review.md) | 产品 / 架构 / 测试三视角独立评审与交叉核对 | 已完成 |
| [05-scope-decision-kq1-b0.md](05-scope-decision-kq1-b0.md) | 范围收口决策与口径冻结 | **生效，实现以此为准** |
| [06-technical-review-kq1-b0.md](06-technical-review-kq1-b0.md) | KQ-1 + B0 技术评审：完成度、P0/P1 发现、行动项与结论 | 已完成（有条件通过，P0 待闭环） |
| [07-implementation-report.md](07-implementation-report.md) | 实现报告：变更清单、42 项测试与双端构建、验收对照、复审重点 | 已完成，待复评 |
| [08-independent-review.md](08-independent-review.md) | 独立复审结论与逐项证据 | 已通过（第三次复验）；非阻断项见 07 |

## 本轮范围

- KQ-1 回答反馈与无答案识别：消息级 👍/👎、原因标签、纠错文本；HYBRID 低置信提示；STRICT 严格拒答；管理端反馈列表与统计。
- B0 产品埋点基线：服务端 6 类事件 + 客户端 3 类事件，有效会话与漏斗口径。
- 安全修复：会话列表/详情/消息接口属主校验，历史无主会话仅管理员可见。

明确不做（顺延）：A1 完整会话管理（列表/重命名/软删/跨设备续聊）、B2 会话洞察页、KQ-2/KQ-3/KQ-5、KQ-4 真实评测、终端用户注册与租户。

## 已确认的关键决策

| 决策 | 结论 |
|---|---|
| 用户身份 | 复用平台账号；归属字段统一 `chat_sessions.created_by`，不新增 `user_id` |
| 会话可见性 | 本人可见；`created_by IS NULL` 的历史会话仅 PLATFORM_ADMIN / DEVELOPER 可见 |
| 隐私 | 原文落库、仅授权角色可见、接口展示脱敏；事件表不记原文 |
| 反馈口径 | 六类原因（PRD 编码）、备注 ≤200 字、同一消息+用户唯一 |
| 拒答 | HYBRID 软提示；STRICT 硬拒答，仅允许向量/混合检索数据集 |
| 迁移 | V11 = 反馈与回答模式（已执行，checksum 保持）；V12 = `product_events`；V13 = 检索诊断/处理状态/原因检索列 |
| 采纳口径 | 复制答案或下载引用算采纳；有效会话 = 追问 / 点赞 / 采纳任一 |

## 下一步

1. 产品/测试按 [07](07-implementation-report.md) 第 5 节复审，重点核对 P0 归属、反馈字段清空、STRICT 数据集限制与 B0 幂等/降级。
2. 在 MySQL 存量库演练 V11/V12，补浏览器 E2E 与真实 Redis 限流验证。
3. 产品确认 PRD 开放问题：点赞原因是否可选、拒答文案是否固定、反馈快照列是否保留。
4. 按「迁移 / M2 安全 / KQ-1 修正 / B0 / 前端 / 测试」拆分提交并把 `mvn test` 与双端构建接入 CI。
