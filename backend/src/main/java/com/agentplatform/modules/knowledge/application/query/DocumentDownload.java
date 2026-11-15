package com.agentplatform.modules.knowledge.application.query;

/**
 * 文档下载结果（应用层只读 DTO）。
 *
 * <p>用于把原始文件内容、文件名与 MIME 类型一起传给接口层；
 * 仅文件类文档会走该 DTO，网页文档直接返回其 sourceUrl。</p>
 *
 * @param name        原始文件名（用于 Content-Disposition）
 * @param contentType MIME 类型，可为空
 * @param content     文件字节
 */
public record DocumentDownload(String name, String contentType, byte[] content) {
}
