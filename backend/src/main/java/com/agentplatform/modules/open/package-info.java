/**
 * 开放平台限界上下文（open）。
 * <p>预留给对外部租户/第三方系统开放的 API 能力（应用凭证、配额、外部调用等）。
 * 当前模块仅声明 Spring Modulith 边界，尚无实现类；后续在该模块内按
 * domain/application/infrastructure/interfaces 四层落地。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "开放平台")
package com.agentplatform.modules.open;
