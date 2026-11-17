package com.agentplatform.modules.insight.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.insight.domain.model.ProductEvent;
import com.agentplatform.modules.insight.domain.model.ProductEventName;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * 产品埋点记录入口（application 层）。
 *
 * <p>核心约束：</p>
 * <ul>
 *   <li>事件写入在主事务提交后异步执行，失败只记 WARN 与指标，不阻断对话/反馈；</li>
 *   <li>客户端只能上报 {@link ProductEventName#clientAllowed()} 的事件，属性按事件白名单过滤；</li>
 *   <li>属性只允许标量值，最多 10 个键、序列化后不超过 1 KB，禁止原文与 PII 进入事件表；</li>
 *   <li>{@code eventId} 唯一，重试只落一条。</li>
 * </ul>
 */
@Service
public class ProductEventRecorder {
    /** 日志只记录事件名与异常类型，不记录属性内容。 */
    private static final Logger log = LoggerFactory.getLogger(ProductEventRecorder.class);
    /** 属性键数量上限；answer_completed 含检索诊断后为 11 个键，留出余量。 */
    private static final int MAX_PROPERTY_KEYS = 16;
    /** 属性序列化后的字节上限。 */
    private static final int MAX_PROPERTY_BYTES = 1024;
    /** 单个字符串属性的最大长度。 */
    private static final int MAX_STRING_LENGTH = 200;
    /** 客户端事件允许携带的属性键，防止原文/PII 混入。 */
    private static final Map<ProductEventName, Set<String>> CLIENT_KEYS = Map.of(
            ProductEventName.AGENT_OPENED, Set.of(),
            ProductEventName.ANSWER_COPIED, Set.of("length"),
            ProductEventName.CITATION_DOWNLOADED, Set.of("documentId", "sourceType"));

    private final ProductEventWriter writer;
    private final Executor executor;
    private final ObjectMapper objectMapper;
    private final ClientEventRateLimiter rateLimiter;
    private final MeterRegistry meters;
    private final boolean enabled;

    /**
     * @param writer       独立事务写入器
     * @param executor     埋点线程池
     * @param objectMapper JSON 序列化器
     * @param rateLimiter  客户端事件限流器
     * @param meters       指标注册器
     * @param enabled      埋点总开关，故障时可快速关闭
     */
    public ProductEventRecorder(ProductEventWriter writer,
                                @Qualifier("eventExecutor") Executor executor,
                                ObjectMapper objectMapper,
                                ClientEventRateLimiter rateLimiter,
                                MeterRegistry meters,
                                @Value("${app.insight.events-enabled:true}") boolean enabled) {
        this.writer = writer;
        this.executor = executor;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.meters = meters;
        this.enabled = enabled;
    }

    /**
     * 记录新会话创建。
     *
     * @param userId    操作人
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param source    来源：user_portal / admin_debug / internal
     */
    public void sessionStarted(String userId, String sessionId, String agentId, String source) {
        recordServer(ProductEventName.SESSION_STARTED, userId, sessionId, null, agentId,
                Map.of("source", safe(source)));
    }

    /**
     * 记录用户消息发送。
     *
     * @param userId    操作人
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param messageId 用户消息 ID
     * @param turnIndex 轮次（从 1 开始）
     * @param length    消息长度
     */
    public void messageSent(String userId, String sessionId, String agentId, String messageId,
                            int turnIndex, int length) {
        recordServer(ProductEventName.MESSAGE_SENT, userId, sessionId, messageId, agentId,
                Map.of("turnIndex", turnIndex, "length", length));
    }

    /**
     * 记录回答完成（含拒答与检索诊断）。
     *
     * @param userId        操作人
     * @param sessionId     会话 ID
     * @param agentId       智能体 ID
     * @param messageId     助手消息 ID
     * @param answerType    ANSWER / REFUSAL
     * @param latencyMs     耗时
     * @param tokens        token 估算
     * @param model         模型名
     * @param mock          是否演示流
     * @param citationCount 引用数
     * @param toolCount     工具调用数
     * @param hitCount      检索命中数
     * @param topScore      检索最高分
     * @param retrievalMode 检索模式
     * @param noHit         是否 0 命中
     */
    public void answerCompleted(String userId, String sessionId, String agentId, String messageId,
                                String answerType, long latencyMs, int tokens, String model, boolean mock,
                                int citationCount, int toolCount, int hitCount, double topScore,
                                String retrievalMode, boolean noHit) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("answerType", answerType);
        properties.put("latencyMs", latencyMs);
        properties.put("tokens", tokens);
        properties.put("model", safe(model));
        properties.put("mock", mock);
        properties.put("citationCount", citationCount);
        properties.put("toolCount", toolCount);
        properties.put("hitCount", hitCount);
        properties.put("topScore", topScore);
        properties.put("mode", safe(retrievalMode));
        properties.put("noHit", noHit);
        recordServer(ProductEventName.ANSWER_COMPLETED, userId, sessionId, messageId, agentId, properties);
    }

    /**
     * 记录回答失败。
     *
     * @param userId    操作人
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param stage     失败阶段：model / tool / timeout
     */
    public void answerFailed(String userId, String sessionId, String agentId, String stage) {
        recordServer(ProductEventName.ANSWER_FAILED, userId, sessionId, null, agentId,
                Map.of("stage", safe(stage)));
    }

    /**
     * 记录反馈提交。
     *
     * @param userId      操作人
     * @param sessionId   会话 ID
     * @param agentId     智能体 ID
     * @param messageId   消息 ID
     * @param rating      UP / DOWN
     * @param reasons     原因编码列表
     * @param hasComment  是否填写备注
     * @param followUp    是否留下问题
     */
    public void feedbackSubmitted(String userId, String sessionId, String agentId, String messageId,
                                  String rating, List<String> reasons, boolean hasComment, boolean followUp) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("rating", rating);
        properties.put("reasons", reasons == null ? "" : String.join(",", reasons));
        properties.put("hasComment", hasComment);
        properties.put("followUpRequested", followUp);
        recordServer(ProductEventName.FEEDBACK_SUBMITTED, userId, sessionId, messageId, agentId, properties);
    }

    /**
     * 记录会话删除。
     *
     * @param userId    操作人
     * @param sessionId 会话 ID
     * @param agentId   智能体 ID
     * @param turns     轮次
     */
    public void sessionDeleted(String userId, String sessionId, String agentId, int turns) {
        recordServer(ProductEventName.SESSION_DELETED, userId, sessionId, null, agentId,
                Map.of("turns", turns));
    }

    /**
     * 记录客户端事件；事件名、属性与限流不合法时抛出业务异常。
     *
     * @param name       事件编码
     * @param eventId    客户端事件 ID；为空时服务端生成
     * @param userId     当前登录用户（服务端覆盖）
     * @param sessionId  会话 ID，可为空
     * @param messageId  消息 ID，可为空
     * @param agentId    智能体 ID，可为空
     * @param properties 属性，按事件白名单过滤
     * @throws BusinessException 事件名不允许、属性非法或超限时抛出
     */
    public void recordClientEvent(String name, String eventId, String userId, String sessionId,
                                  String messageId, String agentId, Map<String, Object> properties) {
        if (!enabled) return;
        ProductEventName eventName = ProductEventName.fromWire(name);
        if (eventName == null || !eventName.clientAllowed()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的事件名");
        }
        // 回答复制/引用下载必须归属会话，避免无主事件污染采纳口径。
        if ((eventName == ProductEventName.ANSWER_COPIED || eventName == ProductEventName.CITATION_DOWNLOADED)
                && (sessionId == null || sessionId.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件必须携带会话");
        }
        if (eventName == ProductEventName.AGENT_OPENED && (agentId == null || agentId.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件必须携带智能体");
        }
        if (eventId != null && eventId.length() > 64) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件 ID 过长");
        }
        rateLimiter.check(userId);
        Map<String, Object> allowed = filterClientProperties(eventName, properties);
        Map<String, Object> safeProperties = sanitize(allowed);
        String id = eventId == null || eventId.isBlank()
                ? UUID.randomUUID().toString().replace("-", "") : eventId;
        dispatch(new ProductEvent(id, eventName.wireName(), userId, sessionId, messageId, agentId,
                safeProperties, Instant.now()));
    }

    /**
     * 服务端事件统一入口；任何异常都吞掉，保证不影响主流程。
     *
     * @param name       事件名
     * @param userId     操作人
     * @param sessionId  会话 ID
     * @param messageId  消息 ID
     * @param agentId    智能体 ID
     * @param properties 属性
     */
    private void recordServer(ProductEventName name, String userId, String sessionId, String messageId,
                              String agentId, Map<String, Object> properties) {
        if (!enabled) return;
        try {
            dispatch(new ProductEvent(UUID.randomUUID().toString().replace("-", ""), name.wireName(),
                    userId, sessionId, messageId, agentId, sanitize(properties), Instant.now()));
        } catch (RuntimeException e) {
            writeFailed(name.wireName(), e);
        }
    }

    /**
     * 主事务提交后投递到埋点线程池；无事务时直接投递。
     *
     * @param event 事件
     */
    private void dispatch(ProductEvent event) {
        Runnable task = () -> writeSafely(event);
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submit(task);
                    }
                });
            } else {
                submit(task);
            }
        } catch (RuntimeException e) {
            writeFailed(event.eventName(), e);
        }
    }

    /**
     * 投递任务，队列满时降级记录。
     *
     * @param task 写入任务
     */
    private void submit(Runnable task) {
        try {
            executor.execute(task);
        } catch (RuntimeException e) {
            writeFailed("dispatch", e);
        }
    }

    /**
     * 在工作线程中写入；重复事件按幂等忽略，其他异常只记录。
     *
     * @param event 事件
     */
    private void writeSafely(ProductEvent event) {
        try {
            writer.write(event);
        } catch (DuplicateKeyException ignored) {
            // eventId 唯一：重试或重复上报只保留一条。
        } catch (RuntimeException e) {
            writeFailed(event.eventName(), e);
        }
    }

    /**
     * 记录埋点失败指标与日志，不输出属性内容。
     *
     * @param eventName 事件名
     * @param error     异常
     */
    private void writeFailed(String eventName, RuntimeException error) {
        log.warn("埋点写入失败 event={} error={}", eventName, error.getClass().getSimpleName());
        meters.counter("insight_event_record_failed_total", "event", eventName).increment();
    }

    /**
     * 按事件类型过滤客户端属性，未知键直接丢弃。
     *
     * @param name       事件名
     * @param properties 原始属性
     * @return 过滤后的属性
     */
    private Map<String, Object> filterClientProperties(ProductEventName name, Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) return Map.of();
        Set<String> allowedKeys = CLIENT_KEYS.getOrDefault(name, Set.of());
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey() != null && allowedKeys.contains(entry.getKey())) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    /**
     * 校验并规范化属性：只允许标量、限制键数与总大小。
     *
     * @param properties 原始属性
     * @return 不可变属性
     * @throws BusinessException 属性过多、类型非法或过大时抛出
     */
    private Map<String, Object> sanitize(Map<String, Object> properties) {
        if (properties == null || properties.isEmpty()) return Map.of();
        if (properties.size() > MAX_PROPERTY_KEYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件属性过多");
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (key == null || key.isBlank() || key.length() > 64 || value == null) continue;
            if (value instanceof String text) {
                safe.put(key, text.length() <= MAX_STRING_LENGTH ? text
                        : text.substring(0, MAX_STRING_LENGTH));
            } else if (value instanceof Integer || value instanceof Long || value instanceof Boolean) {
                safe.put(key, value);
            } else if (value instanceof Double number && Double.isFinite(number)) {
                safe.put(key, number);
            } else if (value instanceof Float number && Float.isFinite(number)) {
                safe.put(key, number);
            } else {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件属性只支持标量值");
            }
        }
        try {
            if (objectMapper.writeValueAsBytes(safe).length > MAX_PROPERTY_BYTES) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件属性过大");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "事件属性序列化失败");
        }
        return Map.copyOf(safe);
    }

    /**
     * 字符串属性兜底为空串，避免 null 进入事件。
     *
     * @param value 原始值
     * @return 非空字符串
     */
    private static String safe(String value) {
        return value == null ? "" : value.length() <= MAX_STRING_LENGTH ? value
                : value.substring(0, MAX_STRING_LENGTH);
    }
}
