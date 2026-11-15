/**
 * 工具与 MCP 限界上下文（Spring Modulith 模块）。
 *
 * <p>职责范围：
 * <ul>
 *   <li>平台工具目录的维护、启用/停用与健康检查；</li>
 *   <li>工具的模拟/HTTP/MCP 执行入口；</li>
 *   <li>MCP 服务接入，支持 Streamable HTTP、SSE、stdio 三种传输；</li>
 *   <li>MCP 工具发现、目录同步、调用执行与调用审计。</li>
 * </ul>
 *
 * <p>分层约束：domain 只依赖仓储端口，infrastructure 实现协议与持久化，
 * application 编排用例，interfaces 暴露 REST 接口。跨模块只允许依赖
 * {@code application/query} 与 {@code application/service}（均已声明
 * {@code @NamedInterface("api")}），domain/infrastructure 不对外暴露。
 */
@org.springframework.modulith.ApplicationModule(displayName = "工具与 MCP")
package com.agentplatform.modules.tool;
