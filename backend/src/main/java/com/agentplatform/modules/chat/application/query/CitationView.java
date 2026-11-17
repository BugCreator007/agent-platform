package com.agentplatform.modules.chat.application.query;

/**
 * 用户端消息引用视图（application/query 层）。
 *
 * <p>只暴露文档展示与下载所需的字段，不把 chat 领域对象带到 portal 模块。</p>
 *
 * @param documentId 文档 ID
 * @param name       文档名称
 * @param url        可下载/打开的链接
 * @param sourceType 来源类型：WEB / FILE
 */
public record CitationView(String documentId, String name, String url, String sourceType) {
}
