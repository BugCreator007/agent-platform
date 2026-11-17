/**
 * 产品洞察应用服务（跨模块 API）。
 *
 * <p>以 {@code @NamedInterface("api")} 暴露埋点记录能力，供 chat 与 portal 调用；
 * 该模块不反向依赖 chat，避免与 chat → insight 的埋点依赖形成循环。</p>
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.insight.application.service;
