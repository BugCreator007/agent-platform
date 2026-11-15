package com.agentplatform.modules.chat.infrastructure.ai;

import com.agentplatform.modules.model.application.query.ModelRoute;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 模型客户端统一抽象（infrastructure 层出站端口）。
 *
 * <p>屏蔽 OpenAI-compatible 与 Anthropic 两种协议差异；实现类负责把通用
 * {@link AiMessage} 转换为供应商请求，并把 SSE 响应解析为文本或 {@link ChatChunk} 流。</p>
 */
public interface ChatModelClient {
    /**
     * 发起纯文本流式补全。
     *
     * @param route        模型路由（含 endpoint、密钥与上游模型名）
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @return 文本增量流；调用失败时以 {@code Flux.error} 结束
     */
    Flux<String> stream(ModelRoute route, String systemPrompt, List<AiMessage> history);

    /**
     * 发起支持工具调用的流式补全。
     *
     * @param route        模型路由
     * @param systemPrompt 系统提示词
     * @param history      对话历史
     * @param tools        OpenAI 风格的工具定义列表；为空时退化为纯文本补全
     * @return 文本增量与工具调用增量组成的片段流，末尾以 {@link ChatChunk#finish()} 结束
     */
    Flux<ChatChunk> streamWithTools(ModelRoute route, String systemPrompt,
                                    List<AiMessage> history, List<Map<String, Object>> tools);
}
