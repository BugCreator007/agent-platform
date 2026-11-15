/**
 * 平台系统限界上下文。
 *
 * <p>负责认证（登录/登出/JWT）、成员、API Key 与审计日志等平台级能力。
 * 模块内部按 DDD 四层组织：interfaces（REST/DTO）、application（用例服务）、
 * domain（模型与仓储端口）、infrastructure（MyBatis-Plus 持久化、审计过滤器与种子数据）。
 * 跨模块只能依赖 application 层并向外部声明 {@code @NamedInterface}。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "平台系统")
package com.agentplatform.modules.system;
