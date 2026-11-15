/**
 * 工具模块应用服务（跨模块可调用的 {@code api} 命名接口）。
 *
 * <p>包含工具目录应用服务、MCP 服务管理应用服务、MCP 运行时服务与工具执行服务；
 * 其他模块通过这里暴露的方法使用工具能力，不直接依赖本模块的 domain/infrastructure。
 */
@org.springframework.modulith.NamedInterface("api")
package com.agentplatform.modules.tool.application.service;
