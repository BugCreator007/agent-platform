package com.agentplatform.modules.knowledge.infrastructure.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 文档文本提取器（基础设施层）。
 *
 * <p>按文件扩展名选择解析方式：PDF 使用 PDFBox，DOCX 使用 Apache POI（含表格文本），
 * HTML 去标签后取纯文本，其余类型按 UTF-8 纯文本读取。旧版 .doc 不受支持，需先转换格式。</p>
 *
 * <p>约束：解析失败统一抛出 {@link IllegalStateException}，由摄取服务捕获后将文档标记为 failed；
 * 参数错误（如 .doc）保留为 {@link IllegalArgumentException}。</p>
 */
@Component
public class DocumentTextExtractor {
    /**
     * 从文件字节中提取纯文本。
     *
     * @param filename    文件名，用于判断格式（不区分大小写）
     * @param contentType MIME 类型（当前实现仅作保留，不参与格式判断）
     * @param content     文件原始字节
     * @return 提取出的文本
     * @throws IllegalArgumentException 文件名为旧版 .doc 等不支持的格式时抛出
     * @throws IllegalStateException    解析过程发生异常时抛出
     */
    public String extract(String filename, String contentType, byte[] content) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                // PDFBox 的 Loader 会解析整个文档；try-with-resources 确保底层资源释放
                try (PDDocument doc = Loader.loadPDF(content)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (name.endsWith(".docx")) {
                // DOCX 需同时提取正文段落与表格单元格，表格单元格以 " | " 分隔
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
                    StringBuilder sb = new StringBuilder();
                    doc.getParagraphs().forEach(p -> sb.append(p.getText()).append('\n'));
                    doc.getTables().forEach(t -> t.getRows().forEach(r ->
                            r.getTableCells().forEach(c -> sb.append(c.getText()).append(" | "))));
                    return sb.toString();
                }
            }
            if (name.endsWith(".doc")) {
                throw new IllegalArgumentException("暂不支持旧版 .doc，请转换为 .docx 或 PDF");
            }
            if (name.endsWith(".html") || name.endsWith(".htm")) {
                // 简单正则去标签并压缩空白，不做 DOM 级清洗
                return new String(content, StandardCharsets.UTF_8).replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ");
            }
            return new String(content, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文档解析失败: " + e.getMessage(), e);
        }
    }
}
