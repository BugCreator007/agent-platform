package com.agentplatform.modules.knowledge.infrastructure.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * 网页抓取器（基础设施层）。
 *
 * <p>使用 Jsoup 抓取网页并提取正文纯文本，支持可选 Cookie（用于内部 Wiki）。
 * 为防止 SSRF，默认拒绝解析到本机/内网/链路本地等地址的主机；
 * 仅当显式设置 {@code app.knowledge.web.allow-private=true} 时放开。</p>
 *
 * <p>正文提取优先选择 {@code main/article} 等语义容器，剔除脚本、导航、页脚等噪声节点。</p>
 */
@Component
public class WebPageFetcher {
    /** 是否允许抓取内网/本机地址。 */
    private final boolean allowPrivate;

    /**
     * @param allowPrivate 是否允许内网地址，来自 {@code app.knowledge.web.allow-private}
     */
    public WebPageFetcher(@Value("${app.knowledge.web.allow-private:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    /**
     * 抓取网页（不带 Cookie）。
     *
     * @param rawUrl 网页地址
     * @return 网页标题、正文与最终 URL
     */
    public WebPage fetch(String rawUrl) { return fetch(rawUrl, null); }

    /**
     * 抓取网页并提取正文。
     *
     * @param rawUrl 网页地址，仅支持 http/https
     * @param cookie 可选 Cookie 请求头，空白表示不携带
     * @return 网页标题（缺失时使用主机名）、正文纯文本与最终 URL
     * @throws IllegalArgumentException 地址非法、协议不支持、命中内网限制或正文为空时抛出
     * @throws IllegalStateException    网络请求或解析异常时抛出
     */
    public WebPage fetch(String rawUrl, String cookie) {
        URI uri = validate(rawUrl);
        try {
            var connection = Jsoup.connect(uri.toString())
                    .userAgent("AgentPlatformBot/1.0 (+https://example.com/bot)")
                    .timeout(20_000)
                    // 限制响应体大小，避免超大页面耗尽内存
                    .maxBodySize(5 * 1024 * 1024)
                    .followRedirects(true);
            if (cookie != null && !cookie.isBlank()) connection.header("Cookie", cookie.trim());
            Document doc = connection.get();
            // 重定向后的最终地址需再次校验，防止公网地址跳转到内网地址绕过限制
            validate(uri.resolve(doc.location()).toString());
            // 移除脚本、样式与页面框架等非正文节点
            doc.select("script,style,noscript,nav,footer,header,aside,form,iframe,svg").remove();
            // 优先取常见正文容器，找不到时退回整个 body
            Element main = doc.selectFirst("main, article, .wiki-content, .markdown-body, #content, #main-content");
            String text = (main == null ? doc.body() : main).text();
            if (text == null || text.isBlank()) throw new IllegalArgumentException("网页没有可提取的正文");
            String title = doc.title();
            return new WebPage(title == null || title.isBlank() ? uri.getHost() : title.trim(), text, uri.toString());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("抓取网页失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验 URL 合法性与目标地址安全性。
     *
     * <p>仅允许 http/https；默认禁止解析到任意本地、回环、站点内网、链路本地与组播地址，
     * 以缓解 SSRF 风险。</p>
     *
     * @param raw 原始地址
     * @return 解析后的 URI
     * @throws IllegalArgumentException 地址格式非法、协议不支持或命中内网限制时抛出
     */
    private URI validate(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("只支持 http/https 网页");
            }
            if (uri.getHost() == null) throw new IllegalArgumentException("网页地址缺少主机名");
            if (!allowPrivate) {
                // 逐个解析 DNS 结果并检查地址类别，避免 DNS 解析到内网 IP
                for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                    if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                            || address.isLinkLocalAddress() || address.isMulticastAddress()) {
                        throw new IllegalArgumentException("出于安全限制，不能抓取内网或本机地址；可设置 app.knowledge.web.allow-private=true");
                    }
                }
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的网页地址: " + raw);
        }
    }

    /**
     * 网页抓取结果。
     *
     * @param title 网页标题（缺失时为域名）
     * @param text  提取后的正文纯文本
     * @param url   请求的最终 URL
     */
    public record WebPage(String title, String text, String url) {}
}
