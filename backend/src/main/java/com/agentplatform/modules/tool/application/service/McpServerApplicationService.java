package com.agentplatform.modules.tool.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.tool.application.query.McpCallOutcome;
import com.agentplatform.modules.tool.application.query.McpServerView;
import com.agentplatform.modules.tool.application.query.McpToolView;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.domain.model.McpTool;
import com.agentplatform.modules.tool.domain.repository.McpServerRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolCallRepository;
import com.agentplatform.modules.tool.domain.repository.McpToolRepository;
import com.agentplatform.modules.tool.domain.repository.ToolRepository;
import com.agentplatform.modules.tool.infrastructure.mcp.McpClientFactory;
import com.agentplatform.modules.tool.infrastructure.mcp.McpSession;
import com.agentplatform.modules.tool.infrastructure.mcp.McpToolDefinition;
import com.agentplatform.modules.tool.infrastructure.mcp.McpUrlValidator;
import com.agentplatform.modules.tool.interfaces.dto.McpServerRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MCP 服务管理应用服务（application 层）。
 *
 * <p>编排 MCP 服务的增删改查、连接/同步（工具发现）、工具启停与调用审计查询。
 * 关键约束：
 * <ul>
 *   <li>创建/更新时校验传输方式与端点：stdio 必须有命令，HTTP/SSE 必须通过
 *       {@link McpUrlValidator} 的私网/协议校验；</li>
 *   <li>连接成功才同步工具目录并置为 {@code CONNECTED}，失败则记录错误并抛业务异常；</li>
 *   <li>删除服务时级联清理其工具与对应工具目录条目（事务内完成）。</li>
 * </ul>
 */
@Service
public class McpServerApplicationService {
    /** MCP 服务仓储。 */
    private final McpServerRepository servers;
    /** MCP 工具仓储。 */
    private final McpToolRepository tools;
    /** 调用审计仓储。 */
    private final McpToolCallRepository calls;
    /** 工具目录仓储，用于把发现的 MCP 工具同步进统一工具目录。 */
    private final ToolRepository catalog;
    /** MCP 客户端工厂。 */
    private final McpClientFactory factory;
    /** MCP 运行时服务，用于工具调用。 */
    private final McpRuntimeService runtime;
    /** MCP URL 安全校验器。 */
    private final McpUrlValidator urlValidator;

    /**
     * 构造服务管理应用服务。
     *
     * @param servers      MCP 服务仓储
     * @param tools        MCP 工具仓储
     * @param calls        调用审计仓储
     * @param catalog      工具目录仓储
     * @param factory      MCP 客户端工厂
     * @param runtime      MCP 运行时服务
     * @param urlValidator MCP URL 校验器
     */
    public McpServerApplicationService(McpServerRepository servers, McpToolRepository tools,
                                       McpToolCallRepository calls, ToolRepository catalog,
                                       McpClientFactory factory, McpRuntimeService runtime,
                                       McpUrlValidator urlValidator) {
        this.servers=servers; this.tools=tools; this.calls=calls; this.catalog=catalog;
        this.factory=factory; this.runtime=runtime; this.urlValidator=urlValidator;
    }

    /**
     * 查询全部 MCP 服务。
     *
     * @return 服务视图列表，包含每个服务的工具数量
     */
    public List<McpServerView> list() {
        return servers.findAll().stream().map(this::view).toList();
    }

    /**
     * 查询单个 MCP 服务。
     *
     * @param id 服务主键
     * @return 服务视图
     * @throws BusinessException 服务不存在（{@code NOT_FOUND}）
     */
    public McpServerView get(String id) { return view(require(id)); }

    /**
     * 创建 MCP 服务。
     *
     * <p>传输方式会归一化；未指定鉴权类型时默认 {@code bearer}；未指定启用状态时默认启用。
     * 新建服务初始状态为 {@code DISCONNECTED}。
     *
     * @param request 创建请求
     * @return 创建后的服务视图
     * @throws BusinessException 名称/传输方式/端点校验失败（{@code VALIDATION_ERROR}）
     */
    public McpServerView create(McpServerRequest request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "MCP 服务名称不能为空");
        String transport = normalizeTransport(request.transport());
        validateEndpoint(transport, request.url(), request.command());
        McpServer server = new McpServer("mcp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10),
                name, transport, request.url(), request.command(), request.args(),
                request.authType() == null || request.authType().isBlank() ? "bearer" : request.authType(),
                request.authToken(), request.enabled() == null || request.enabled(), "DISCONNECTED", null, null);
        servers.save(server);
        return view(server);
    }

    /**
     * 更新 MCP 服务配置。
     *
     * <p>未提供的字段沿用原值；更新后强制置为 {@code DISCONNECTED}，
     * 因为旧连接可能已不匹配新配置，需要重新连接/同步。
     *
     * @param id      服务主键
     * @param request 更新请求（字段可为 {@code null} 表示不修改）
     * @return 更新后的服务视图
     * @throws BusinessException 服务不存在或端点校验失败
     */
    public McpServerView update(String id, McpServerRequest request) {
        McpServer server = require(id);
        String transport = request.transport() == null ? server.transport() : normalizeTransport(request.transport());
        validateEndpoint(transport, request.url() == null ? server.url() : request.url(),
                request.command() == null ? server.command() : request.command());
        server.update(request.name(), transport, request.url(), request.command(), request.args(),
                request.authType(), request.authToken(), request.enabled());
        // 配置变更后既有连接失效，必须重新连接才能发现工具。
        server.disconnected();
        servers.save(server);
        return view(server);
    }

    /**
     * 查询某服务下已同步的工具。
     *
     * @param serverId 服务主键
     * @return 工具视图列表
     * @throws BusinessException 服务不存在
     */
    public List<McpToolView> tools(String serverId) {
        require(serverId);
        return tools.findByServer(serverId).stream().map(this::toolView).toList();
    }

    /**
     * 连接 MCP 服务并同步工具目录。
     *
     * <p>流程：按服务配置打开会话 → 调用 {@code tools/list} 发现工具 → 写入工具表
     * 与统一工具目录 → 标记连接成功。任一步失败都会把服务状态置为 {@code ERROR}
     * 并保存错误信息。
     *
     * @param id 服务主键
     * @return 连接后的服务视图
     * @throws BusinessException 服务不存在（{@code NOT_FOUND}）或连接失败（{@code STATE_CONFLICT}）
     */
    public McpServerView connect(String id) {
        McpServer server = require(id);
        try (McpSession session = factory.open(server)) {
            List<McpToolDefinition> discovered = session.listTools();
            syncTools(server, discovered);
            server.connected();
            servers.save(server);
            return view(server);
        } catch (Exception e) {
            server.failed(e.getMessage());
            servers.save(server);
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "MCP 连接失败: " + e.getMessage());
        }
    }

    /**
     * 重新同步 MCP 工具（语义与 {@link #connect(String)} 相同）。
     *
     * @param id 服务主键
     * @return 同步后的服务视图
     * @throws BusinessException 服务不存在或同步失败
     */
    public McpServerView sync(String id) { return connect(id); }

    /**
     * 调用指定 MCP 工具。
     *
     * @param toolKey   平台工具键
     * @param arguments 工具入参
     * @return 调用结果；工具级错误通过 {@code error=true} 表达
     * @throws IllegalArgumentException 工具或服务不存在
     * @throws IllegalStateException    工具或服务已停用
     */
    public McpCallOutcome call(String toolKey, Map<String, Object> arguments) {
        return runtime.execute(toolKey, arguments);
    }

    /**
     * 切换 MCP 工具启用状态，并同步统一工具目录中的对应条目。
     *
     * <p>目录条目名称与 {@code toolKey} 一致；只有当目录条目存在且状态不一致时才重写，
     * 避免无意义写库。
     *
     * @param id MCP 工具主键
     * @return 切换后的工具视图
     * @throws BusinessException 工具不存在（{@code NOT_FOUND}）
     */
    public McpToolView toggleTool(String id) {
        McpTool tool = tools.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP 工具不存在"));
        tool.toggle();
        tools.save(tool);
        catalog.findByName(tool.toolKey()).ifPresent(t -> {
            if (t.enabled() != tool.enabled()) catalog.save(new com.agentplatform.modules.tool.domain.model.Tool(
                    t.name(), t.type(), t.owner(), t.calls(), t.errorRate(), t.health(), t.checked(),
                    tool.enabled(), t.description(), t.endpoint()));
        });
        return toolView(tool);
    }

    /**
     * 删除 MCP 服务，并级联删除其工具与统一工具目录条目。
     *
     * <p>整个操作在事务内执行，避免出现“服务已删但工具残留”或反向的中间态。
     *
     * @param id 服务主键
     * @throws BusinessException 服务不存在
     */
    @Transactional
    public void delete(String id) {
        McpServer server = require(id);
        for (McpTool tool : tools.findByServer(id)) catalog.deleteByName(tool.toolKey());
        tools.deleteByServer(id);
        servers.deleteById(id);
    }

    /**
     * 查询某服务最近的调用审计记录。
     *
     * @param id    服务主键
     * @param limit 最多返回条数
     * @return 调用记录列表（按时间倒序）
     * @throws BusinessException 服务不存在
     */
    public List<?> recentCalls(String id, int limit) {
        require(id);
        return calls.recent(id, limit);
    }

    /**
     * 同步发现的工具到工具表与统一工具目录（upsert）。
     *
     * <p>工具键固定为 {@code serverId.toolName}；已存在的工具保留主键与启用状态，
     * 新工具默认启用。风险级别按工具名关键字推断。
     *
     * @param server     所属 MCP 服务
     * @param discovered MCP 服务端返回的工具定义
     */
    private void syncTools(McpServer server, List<McpToolDefinition> discovered) {
        for (McpToolDefinition definition : discovered) {
            String key = server.id() + "." + definition.name();
            McpTool existing = tools.findByKey(key).orElse(null);
            String id = existing == null ? "mt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) : existing.id();
            McpTool tool = new McpTool(id, server.id(), key, definition.name(), definition.description(),
                    definition.inputSchema(), existing == null || existing.enabled(), risk(definition.name()));
            tools.save(tool);
            catalog.save(new com.agentplatform.modules.tool.domain.model.Tool(
                    key, "MCP", server.name(), 0, 0, "healthy", "刚刚", tool.enabled(),
                    definition.description(), server.url()));
        }
    }

    /**
     * 按工具名关键字推断风险级别。
     *
     * <p>名称包含 delete/remove/write/update 视为写操作，其余视为读操作。
     * 这是启发式判断，仅用于展示与审批参考。
     *
     * @param name 服务端工具名
     * @return {@code write} 或 {@code read}
     */
    private String risk(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("delete") || n.contains("remove") || n.contains("write") || n.contains("update") ? "write" : "read";
    }

    /**
     * 归一化传输方式，并拒绝不支持的取值。
     *
     * <p>{@code http}/{@code streamable_http} 统一为 {@code streamable-http}；
     * 未提供时默认 {@code streamable-http}。
     *
     * @param transport 原始传输方式，可为 {@code null}
     * @return 归一化后的传输方式
     * @throws BusinessException 取值不受支持（{@code VALIDATION_ERROR}）
     */
    private String normalizeTransport(String transport) {
        String t = transport == null ? "streamable-http" : transport.trim().toLowerCase(Locale.ROOT);
        if (t.equals("http") || t.equals("streamable_http")) return "streamable-http";
        if (!t.equals("streamable-http") && !t.equals("sse") && !t.equals("stdio")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的 MCP 传输方式: " + transport);
        }
        return t;
    }

    /**
     * 按传输方式校验端点配置。
     *
     * <p>stdio 必须有非空命令；HTTP/SSE 必须有 URL 且通过私网/协议安全校验，
     * 防止服务端被用于访问本机、内网或非 HTTP(S) 资源（SSRF 防护）。
     *
     * @param transport 传输方式
     * @param url       HTTP/SSE 地址
     * @param command   stdio 启动命令
     * @throws BusinessException 校验失败（{@code VALIDATION_ERROR}）
     */
    private void validateEndpoint(String transport, String url, String command) {
        if ("stdio".equals(transport)) {
            if (command == null || command.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "stdio 需要启动命令");
        } else {
            if (url == null || url.isBlank()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "HTTP/SSE 传输需要 URL");
            try { urlValidator.validate(url); } catch (IllegalArgumentException e) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, e.getMessage());
            }
        }
    }

    /**
     * 按主键加载服务，不存在时抛出业务异常。
     *
     * @param id 服务主键
     * @return 服务领域对象
     * @throws BusinessException 服务不存在（{@code NOT_FOUND}）
     */
    private McpServer require(String id) {
        return servers.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "MCP 服务不存在: " + id));
    }

    /**
     * 将领域对象转换为对外视图（不包含鉴权令牌）。
     *
     * @param s 服务领域对象
     * @return 服务视图
     */
    private McpServerView view(McpServer s) {
        return new McpServerView(s.id(), s.name(), s.transport(), s.url(), s.command(), s.args(), s.authType(),
                s.enabled(), s.status(), s.lastError(), s.lastConnectedAt(), tools.findByServer(s.id()).size());
    }

    /**
     * 将 MCP 工具领域对象转换为对外视图。
     *
     * @param t 工具领域对象
     * @return 工具视图
     */
    private McpToolView toolView(McpTool t) {
        return new McpToolView(t.id(), t.serverId(), t.toolKey(), t.name(), t.description(), t.inputSchema(),
                t.enabled(), t.riskLevel());
    }
}
