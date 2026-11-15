package com.agentplatform.modules.chat.application.runtime;

import com.agentplatform.modules.chat.infrastructure.ai.ModelGateway;
import org.springframework.stereotype.Service;

/**
 * 轻量同步补全服务（application 层）。
 *
 * <p>供其他模块或内部任务在不需要 SSE 的场景下调用默认模型路由，例如生成摘要、分类等。
 * 底层复用 {@link ModelGateway}，无可用密钥时返回本地演示文本。</p>
 */
@Service
public class ChatCompletionService {
    private final ModelGateway gateway;

    public ChatCompletionService(ModelGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 使用默认模型路由执行一次同步补全。
     *
     * @param systemPrompt 系统提示词
     * @param userMessage  用户消息
     * @return 模型输出全文；无可用模型时为本地演示文本
     */
    public String complete(String systemPrompt, String userMessage) {
        return gateway.complete(gateway.defaultRoutes(), systemPrompt, userMessage);
    }

    /**
     * 判断默认路由中是否存在可用的模型配置。
     *
     * @return 至少一条路由可用时为 true；否则为 false（调用将走本地演示模式）
     */
    public boolean configured() {
        return gateway.configured(gateway.defaultRoutes());
    }
}
