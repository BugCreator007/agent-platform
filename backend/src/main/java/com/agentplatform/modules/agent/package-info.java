/**
 * 智能体限界上下文（agent）。
 * <p>负责智能体定义、模型绑定与生命周期状态流转（草稿/运行/灰度/暂停）。
 * 作为独立的 Spring Modulith 应用模块，仅通过 {@code application.query} 与
 * {@code application.service} 两个 {@code @NamedInterface("api")} 包向其他模块暴露能力，
 * domain 与 infrastructure 不跨模块访问。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "智能体")
package com.agentplatform.modules.agent;
