/**
 * 工具模块应用层。
 *
 * <p>负责编排领域对象与基础设施端口，实现工具目录维护、MCP 服务生命周期
 * （创建、连接、同步、删除）以及工具调用等用例；不直接处理 HTTP 协议细节，
 * 也不直接依赖 MyBatis 或具体 MCP 客户端实现。
 */
package com.agentplatform.modules.tool.application;
