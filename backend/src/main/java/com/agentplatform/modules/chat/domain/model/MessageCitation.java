package com.agentplatform.modules.chat.domain.model;

/**
 * 会话消息中的知识库引用（领域值对象）。
 *
 * <p>只保存生成回答时实际使用的文档信息：文档 ID、展示名、可访问链接与来源类型。
 * 网页来源的 {@code url} 为原始页面地址；文件来源为平台受保护的下载接口。</p>
 *
 * @param documentId 文档 ID
 * @param name       文档展示名
 * @param url        可引用链接
 * @param sourceType 来源类型：WEB / FILE
 */
public record MessageCitation(String documentId, String name, String url, String sourceType) {
}
