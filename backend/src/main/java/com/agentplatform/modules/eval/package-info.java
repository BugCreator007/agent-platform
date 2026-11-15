/**
 * 评测中心限界上下文（eval）。
 * <p>负责评测集、评测运行记录以及发布前的评测门禁。作为独立的 Spring Modulith
 * 应用模块，仅通过 {@code application.service} 的 {@code @NamedInterface("api")}
 * 向发布模块暴露 {@code EvalGateService} 等用例。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "评测中心")
package com.agentplatform.modules.eval;
