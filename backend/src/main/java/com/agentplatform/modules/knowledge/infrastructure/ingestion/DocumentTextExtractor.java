package com.agentplatform.modules.knowledge.infrastructure.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class DocumentTextExtractor {
    public String extract(String filename, String contentType, byte[] content) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(content)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            if (name.endsWith(".docx")) {
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
