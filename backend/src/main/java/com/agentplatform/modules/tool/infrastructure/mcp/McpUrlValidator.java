package com.agentplatform.modules.tool.infrastructure.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

@Component
public class McpUrlValidator {
    private final boolean allowPrivate;
    public McpUrlValidator(@Value("${app.mcp.allow-private-urls:true}") boolean allowPrivate) { this.allowPrivate=allowPrivate; }

    public void validate(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("MCP URL 只支持 http/https");
            }
            if (uri.getHost() == null) throw new IllegalArgumentException("MCP URL 缺少主机名");
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLinkLocalAddress() || address.isAnyLocalAddress()) {
                    throw new IllegalArgumentException("不允许 MCP 访问链路本地或任意地址");
                }
                if (!allowPrivate && (address.isLoopbackAddress() || address.isSiteLocalAddress())) {
                    throw new IllegalArgumentException("当前禁止 MCP 访问内网/本机地址，可设置 MCP_ALLOW_PRIVATE_URLS=true");
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 MCP URL: " + raw);
        }
    }
}
