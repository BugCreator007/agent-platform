package com.agentplatform.modules.tool.infrastructure.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;

/**
 * MCP URL 安全校验器（infrastructure 层）。
 *
 * <p>用于 SSRF 防护：只允许 http/https；拒绝链路本地地址与任意地址（0.0.0.0 等）；
 * 默认允许内网/本机（便于本地开发接入），可通过
 * {@code app.mcp.allow-private-urls=false}（环境变量 {@code MCP_ALLOW_PRIVATE_URLS}）
 * 关闭。校验会解析域名并对全部 A/AAAA 记录逐一检查。
 */
@Component
public class McpUrlValidator {
    /** 是否允许访问内网/本机地址。 */
    private final boolean allowPrivate;

    /**
     * 构造校验器。
     *
     * @param allowPrivate 是否允许私网/回环地址
     */
    public McpUrlValidator(@Value("${app.mcp.allow-private-urls:true}") boolean allowPrivate) { this.allowPrivate=allowPrivate; }

    /**
     * 校验 MCP 服务 URL 是否安全可用。
     *
     * @param raw 原始 URL 字符串
     * @throws IllegalArgumentException URL 非法、协议不支持、缺少主机名，或命中禁止的地址类型
     */
    public void validate(String raw) {
        try {
            URI uri = URI.create(raw);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("MCP URL 只支持 http/https");
            }
            if (uri.getHost() == null) throw new IllegalArgumentException("MCP URL 缺少主机名");
            // 必须逐个检查解析结果：DNS 可能返回多个地址（含 IPv6），任一命中即拒绝。
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                // 链路本地地址（169.254.x.x、fe80::/10）常用于云元数据服务，必须禁止。
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
            // DNS 解析失败等异常统一视为 URL 无效。
            throw new IllegalArgumentException("无效的 MCP URL: " + raw);
        }
    }
}
