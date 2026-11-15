/**
 * 启动与系统模块。
 *
 * <p>承载与具体业务无关的应用级入口：服务信息、模块清单等公共接口。
 * 作为 Spring Modulith 的封闭模块，不向其他模块暴露内部实现。</p>
 */
@org.springframework.modulith.ApplicationModule(displayName = "启动与系统")
package com.agentplatform.admin;
