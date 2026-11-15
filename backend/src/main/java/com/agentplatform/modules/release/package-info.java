/**
 * 发布与版本限界上下文（release）。
 * <p>负责智能体版本的发布单管理：审批、灰度推进、流量调整与回滚。
 * 作为独立的 Spring Modulith 应用模块，只通过 {@code application.service}
 * 的 {@code @NamedInterface("api")} 暴露用例，domain/infrastructure 不跨模块访问。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "发布与版本")
package com.agentplatform.modules.release;
