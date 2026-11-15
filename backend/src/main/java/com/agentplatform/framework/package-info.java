/**
 * 技术框架包。
 *
 * <p>承载与业务无关的基础设施：安全（JWT、限流、黑名单、密钥加解密）、
 * 全局异常处理、线程池与 OpenAPI 等配置。对全部模块开放，
 * 但不得依赖任何业务模块的领域/基础设施实现。</p>
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "技术框架")
package com.agentplatform.framework;
