/**
 * 模型厂商限界上下文（Spring Modulith 模块）。
 *
 * <p>负责模型厂商、模型目录与路由策略的维护，是运行时多模型路由的数据来源。
 * 模块内按 DDD 分层：interfaces 暴露 REST，application 承载用例与查询，
 * domain 保存领域模型与仓储端口，infrastructure 提供 MyBatis-Plus 持久化实现。</p>
 *
 * <p>关键约束：厂商 API Key 属于敏感数据，落库前必须经 {@code SecretCipher} 加密，
 * 对外返回只暴露脱敏值；跨模块调用只允许依赖 application/query 或 application/service
 * 中标注了 {@code @NamedInterface} 的能力。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "模型厂商")
package com.agentplatform.modules.model;
