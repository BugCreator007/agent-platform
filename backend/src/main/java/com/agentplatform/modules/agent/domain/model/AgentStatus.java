package com.agentplatform.modules.agent.domain.model;

/**
 * 智能体生命周期状态。
 * <p>状态迁移由聚合根 {@code Agent} 的方法驱动：
 * {@code DRAFT -> RUNNING}（发布）、{@code RUNNING -> PAUSED}（暂停）、
 * {@code PAUSED -> RUNNING}（恢复）；{@code GRAYSCALE} 表示灰度发布中。
 * 状态以枚举名小写形式持久化到数据库。</p>
 */
public enum AgentStatus {
    /** 草稿：已创建但尚未发布，用户门户不可见。 */
    DRAFT,
    /** 运行中：已全量发布，用户门户可发起对话。 */
    RUNNING,
    /** 灰度中：仅按灰度比例对外提供服务，用户门户可见。 */
    GRAYSCALE,
    /** 已暂停：保留配置但暂停对外服务，用户门户不可见。 */
    PAUSED
}
