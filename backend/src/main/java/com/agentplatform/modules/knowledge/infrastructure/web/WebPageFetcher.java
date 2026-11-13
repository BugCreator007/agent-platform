package com.agentplatform.modules.knowledge.infrastructure.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Component
public class WebPageFetcher {
    private final boolean allowPrivate;

    public WebPageFetcher(@Value("${app.knowledge.web.allow-private:false}") boolean allowPrivate) {
        this.allowPrivate = allowPrivate;
    }

    public WebPage fetch(String rawUrl) { return fetch(rawUrl, null); }

    public WebPage fetch(String rawUrl, String cookie) {
        URI uri = validate(rawUrl);
        try {
            var connection = Jsoup.connect(uri.toString())
                    .userAgent("AgentPlatformBot/1.0 (+https://example.com/bot)")
                    .timeout(20_000)
                    .maxBodySize(5 * 1024 * 1024)
                    .followRedirects(true);
            if (cookie != null && !cookie.isBlank()) connection.header("Cookie", cookie.trim());
            Document doc = connection.get();
            validate(uri.resolve(doc.location()).toString());
            doc.select("script,style,noscript,nav,footer,header,aside,form,iframe,svg").remove();
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

    private URI validate(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new IllegalArgumentException("只支持 http/https 网页");
            }
            if (uri.getHost() == null) throw new IllegalArgumentException("网页地址缺少主机名");
            if (!allowPrivate) {
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

    public record WebPage(String title, String text, String url) {}
}
