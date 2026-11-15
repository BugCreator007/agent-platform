package com.agentplatform.modules.chat.infrastructure.ai;

import java.util.List;

/**
 * 模型流式输出片段（infrastructure 层内部模型）。
 *
 * <p>一次流式响应会拆成多个片段：文本片段、工具调用参数增量片段，以及末尾的结束片段。</p>
 *
 * @param content   本片段的文本增量；非文本片段为空字符串
 * @param done      是否为流结束标记
 * @param toolCalls 本片段携带的工具调用增量；无则为空列表
 */
public record ChatChunk(String content, boolean done, List<ToolCallDelta> toolCalls) {
    /**
     * 构造文本增量片段。
     *
     * @param content 文本增量
     * @return 非结束、无工具调用的片段
     */
    public static ChatChunk text(String content) { return new ChatChunk(content, false, List.of()); }

    /**
     * 构造流结束片段。
     *
     * @return 空内容、{@code done=true} 的片段
     */
    public static ChatChunk finish() { return new ChatChunk("", true, List.of()); }
}
