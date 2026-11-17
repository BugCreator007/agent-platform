package com.agentplatform.modules.chat.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.common.text.PiiMasker;
import com.agentplatform.modules.chat.application.query.FeedbackListItem;
import com.agentplatform.modules.chat.application.query.FeedbackPage;
import com.agentplatform.modules.chat.application.query.FeedbackReasonView;
import com.agentplatform.modules.chat.application.query.FeedbackStats;
import com.agentplatform.modules.chat.application.query.FeedbackView;
import com.agentplatform.modules.chat.domain.model.ChatSession;
import com.agentplatform.modules.chat.domain.model.FeedbackHandleStatus;
import com.agentplatform.modules.chat.domain.model.FeedbackRating;
import com.agentplatform.modules.chat.domain.model.FeedbackReason;
import com.agentplatform.modules.chat.domain.model.MessageFeedback;
import com.agentplatform.modules.chat.domain.model.SessionMessage;
import com.agentplatform.modules.chat.domain.repository.FeedbackRepository;
import com.agentplatform.modules.chat.domain.repository.SessionMessageRepository;
import com.agentplatform.modules.chat.domain.repository.SessionRepository;
import com.agentplatform.modules.insight.application.service.ProductEventRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 消息反馈应用服务（application 层）。
 *
 * <p>职责：校验回答归属与原因合法性、生成问题/回答快照、幂等保存与撤销反馈，
 * 以及为管理端提供真实反馈列表与质量汇总。反馈只允许落在助手消息上；
 * 会话存在归属人且与当前用户不一致时返回 403。</p>
 */
@Service
public class FeedbackApplicationService {
    /** 预览最大长度，与 PRD 的列表截断口径（80 字）一致。 */
    private static final int PREVIEW_LIMIT = 80;
    /** 备注最大长度，与数据库 comment 列一致。 */
    private static final int COMMENT_LIMIT = 200;
    /** 日志只记录结构化元数据，不记录问题/回答/备注正文。 */
    private static final Logger log = LoggerFactory.getLogger(FeedbackApplicationService.class);

    private final FeedbackRepository feedbacks;
    private final SessionMessageRepository messages;
    private final SessionRepository sessions;
    private final FeedbackRateLimiter rateLimiter;
    private final ProductEventRecorder events;
    private final MeterRegistry meters;

    public FeedbackApplicationService(FeedbackRepository feedbacks, SessionMessageRepository messages,
                                      SessionRepository sessions, FeedbackRateLimiter rateLimiter,
                                      ProductEventRecorder events, MeterRegistry meters) {
        this.feedbacks = feedbacks;
        this.messages = messages;
        this.sessions = sessions;
        this.rateLimiter = rateLimiter;
        this.events = events;
        this.meters = meters;
    }

    /**
     * 提交或更新一条反馈（幂等）。
     *
     * @param userId    当前登录用户
     * @param messageId 助手消息 ID
     * @param command   反馈内容
     * @return 最新反馈视图
     */
    @Transactional
    public FeedbackView submit(String userId, String messageId, SubmitFeedbackCommand command) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未登录，无法提交反馈");
        }
        rateLimiter.check(userId);
        SessionMessage message = requireAssistantMessage(messageId);
        ChatSession session = requireOwnedSession(message.sessionId(), userId);
        FeedbackRating rating = FeedbackRating.parse(command.rating());
        if (rating == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "评分仅支持 UP 或 DOWN");
        }
        List<FeedbackReason> reasons = parseReasons(rating, command.reasons());
        String comment = normalizeComment(command.comment());
        boolean followUp = rating == FeedbackRating.DOWN && command.followUpRequested();

        MessageFeedback existing = feedbacks.findByMessageAndUser(messageId, userId).orElse(null);
        Instant now = Instant.now();
        MessageFeedback record = new MessageFeedback(
                existing == null ? "fb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                        : existing.id(),
                messageId, session.id(), session.agentId(), userId, rating, reasons, comment, followUp,
                questionPreview(session, message), preview(message.content()),
                existing == null ? now : existing.createdAt(), now,
                existing == null ? FeedbackHandleStatus.OPEN : existing.handleStatus(), session.agentName());
        feedbacks.save(record);
        meters.counter("agent_feedback_total", "rating", rating.name(),
                "agent", session.agentId() == null ? "" : session.agentId()).increment();
        // 只记录评分、消息与智能体，不记录用户备注正文。
        log.info("反馈已提交 user={} message={} rating={} agent={}", userId, messageId, rating,
                session.agentId());
        events.feedbackSubmitted(userId, session.id(), session.agentId(), messageId, rating.name(),
                reasons.stream().map(Enum::name).toList(), comment != null, followUp);
        return toView(record);
    }

    /**
     * 撤销当前用户对某条消息的反馈；不存在时静默成功，保证幂等。
     *
     * @param userId    当前登录用户
     * @param messageId 消息 ID
     */
    @Transactional
    public void revoke(String userId, String messageId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "未登录，无法撤销反馈");
        }
        SessionMessage message = requireAssistantMessage(messageId);
        requireOwnedSession(message.sessionId(), userId);
        feedbacks.deleteByMessageAndUser(messageId, userId);
    }

    /**
     * 查询当前用户在指定会话下的全部反馈，用于前端恢复状态。
     *
     * @param userId    当前登录用户
     * @param sessionId 会话 ID
     * @return 反馈列表
     */
    public List<FeedbackView> listForUser(String userId, String sessionId) {
        requireOwnedSession(sessionId, userId);
        return feedbacks.findBySessionAndUser(sessionId, userId).stream().map(this::toView).toList();
    }

    /**
     * 管理端反馈列表：按时间倒序分页，支持智能体、评分与原因过滤。
     *
     * @param agentId 智能体 ID，可为空
     * @param rating  评分，可为空
     * @param reason  原因编码，可为空
     * @param from    起始时间，可为空（默认近 30 天）
     * @param to      截止时间，可为空（默认当前）
     * @param page    页码，从 1 开始
     * @param size    每页条数，1–100
     * @return 分页结果
     */
    public FeedbackPage list(String agentId, String rating, String reason,
                             Instant from, Instant to, int page, int size) {
        Instant start = from == null ? Instant.now().minus(30, ChronoUnit.DAYS) : from;
        Instant end = to == null ? Instant.now() : to;
        if (!start.isBefore(end)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "时间窗口非法");
        }
        FeedbackReason reasonFilter = null;
        if (reason != null && !reason.isBlank()) {
            reasonFilter = FeedbackReason.parse(reason);
            if (reasonFilter == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知的反馈原因");
            }
        }
        String ratingFilter = null;
        if (rating != null && !rating.isBlank()) {
            FeedbackRating parsed = FeedbackRating.parse(rating);
            if (parsed == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "评分仅支持 UP 或 DOWN");
            }
            ratingFilter = parsed.name();
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = (safePage - 1) * safeSize;
        String reasonCode = reasonFilter == null ? null : reasonFilter.name();
        long total = feedbacks.count(agentId, ratingFilter, reasonCode, start, end);
        if (total == 0 || offset >= total) {
            return new FeedbackPage(List.of(), safePage, safeSize, total);
        }
        List<FeedbackListItem> items = feedbacks.findPage(agentId, ratingFilter, reasonCode,
                        start, end, offset, safeSize).stream()
                .map(this::toListItem)
                .toList();
        return new FeedbackPage(items, safePage, safeSize, total);
    }

    /**
     * 质量汇总：反馈率、好评率、无答案率与原因分布，全部由真实数据计算。
     *
     * @param agentId 智能体 ID，可为空
     * @param days    统计天数，1–90
     * @return 汇总数据
     */
    public FeedbackStats stats(String agentId, int days) {
        int range = Math.max(1, Math.min(days, 90));
        Instant from = Instant.now().minus(range, ChronoUnit.DAYS);
        List<MessageFeedback> rows = feedbacks.find(agentId, null, from);
        long total = rows.size();
        long up = rows.stream().filter(r -> r.rating() == FeedbackRating.UP).count();
        long down = total - up;
        long followUp = rows.stream().filter(MessageFeedback::followUpRequested).count();
        Set<String> feedbackMessages = new LinkedHashSet<>();
        rows.forEach(r -> feedbackMessages.add(r.messageId()));
        long assistant = messages.countAssistant(from, agentId);
        long refusals = messages.countRefusal(from, agentId);

        Map<FeedbackReason, Long> reasonCounts = new LinkedHashMap<>();
        for (MessageFeedback row : rows) {
            if (row.rating() != FeedbackRating.DOWN) continue;
            for (FeedbackReason reason : row.safeReasons()) {
                reasonCounts.merge(reason, 1L, Long::sum);
            }
        }
        List<FeedbackStats.ReasonCount> reasons = new ArrayList<>();
        reasonCounts.entrySet().stream()
                .sorted(Map.Entry.<FeedbackReason, Long>comparingByValue().reversed())
                .forEach(e -> reasons.add(new FeedbackStats.ReasonCount(
                        e.getKey().name(), e.getKey().label(), e.getValue())));

        return new FeedbackStats(agentId, range, total, up, down, feedbackMessages.size(),
                assistant, refusals, followUp,
                ratio(feedbackMessages.size(), assistant), ratio(up, total), ratio(refusals, assistant),
                reasons);
    }

    /**
     * 校验消息存在且为助手回答。
     *
     * @param messageId 消息 ID
     * @return 助手消息
     */
    private SessionMessage requireAssistantMessage(String messageId) {
        SessionMessage message = messages.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "消息不存在"));
        if (!"assistant".equals(message.role())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只能对助手回答反馈");
        }
        return message;
    }

    /**
     * 校验会话存在且归属当前用户；历史会话（created_by 为空）按过渡策略放行。
     *
     * @param sessionId 会话 ID
     * @param userId    当前用户
     * @return 会话
     */
    private ChatSession requireOwnedSession(String sessionId, String userId) {
        ChatSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "会话不存在"));
        // 历史无主会话仅管理员/开发者在管理端可见，用户端不可反馈；统一按不存在处理。
        if (session.createdBy() == null || session.createdBy().isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "会话不存在或缺少归属");
        }
        if (!session.createdBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该会话");
        }
        return session;
    }

    /**
     * 解析并校验原因：点踩必填 1–2 个，点赞可选至多 2 个，去重且必须属于对应评分。
     *
     * @param rating 评分
     * @param codes  原因编码
     * @return 不可变原因列表
     */
    private List<FeedbackReason> parseReasons(FeedbackRating rating, List<String> codes) {
        List<String> input = codes == null ? List.of() : codes;
        if (rating == FeedbackRating.DOWN && (input.isEmpty() || input.size() > 2)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "点踩需要选择 1–2 个原因");
        }
        if (input.size() > 2) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "原因最多选择 2 个");
        }
        List<FeedbackReason> result = new ArrayList<>();
        for (String code : input) {
            FeedbackReason reason = FeedbackReason.parse(code);
            if (reason == null || !reason.allowedFor(rating)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "反馈原因与评分不匹配");
            }
            if (!result.contains(reason)) result.add(reason);
        }
        if (rating == FeedbackRating.DOWN && result.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "点踩需要选择 1–2 个原因");
        }
        return List.copyOf(result);
    }

    /**
     * 归一化备注：去首尾空白并校验长度。
     *
     * @param comment 原始备注
     * @return 归一化后的备注；空串转 null
     */
    private String normalizeComment(String comment) {
        if (comment == null) return null;
        String value = comment.trim();
        if (value.isEmpty()) return null;
        if (value.length() > COMMENT_LIMIT) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "备注不能超过 200 字");
        }
        return value;
    }

    /**
     * 生成提问快照：优先取该回答之前最近一条用户消息，缺失时回退会话主题。
     *
     * @param session 会话
     * @param answer  助手消息
     * @return 截断后的提问摘要
     */
    private String questionPreview(ChatSession session, SessionMessage answer) {
        List<SessionMessage> all = messages.findBySession(answer.sessionId());
        for (int i = all.size() - 1; i >= 0; i--) {
            SessionMessage current = all.get(i);
            if (current.id().equals(answer.id())) {
                for (int j = i - 1; j >= 0; j--) {
                    if ("user".equals(all.get(j).role())) return preview(all.get(j).content());
                }
                break;
            }
        }
        return preview(session.topic());
    }

    /**
     * 截断文本用于列表展示。
     *
     * @param text 原始文本
     * @return 单行且不超过 PREVIEW_LIMIT 的摘要
     */
    private String preview(String text) {
        if (text == null) return "";
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= PREVIEW_LIMIT ? singleLine
                : singleLine.substring(0, PREVIEW_LIMIT) + "…";
    }

    private FeedbackView toView(MessageFeedback f) {
        return new FeedbackView(f.id(), f.messageId(),
                f.rating() == null ? null : f.rating().name(), reasonViews(f), f.comment(),
                f.followUpRequested(), f.createdAt(), f.updatedAt());
    }

    private FeedbackListItem toListItem(MessageFeedback f) {
        // 管理端出口统一脱敏，避免手机号/证件号/银行卡在列表中原文暴露。
        return new FeedbackListItem(f.id(), f.messageId(), f.sessionId(), f.agentId(), f.agentName(),
                f.userId(), f.rating() == null ? null : f.rating().name(), reasonViews(f),
                PiiMasker.mask(f.comment()),
                f.followUpRequested(), PiiMasker.mask(f.questionPreview()), PiiMasker.mask(f.answerPreview()),
                f.createdAt(),
                f.handleStatus() == null ? FeedbackHandleStatus.OPEN.name() : f.handleStatus().name());
    }

    private List<FeedbackReasonView> reasonViews(MessageFeedback f) {
        return f.safeReasons().stream()
                .map(r -> new FeedbackReasonView(r.name(), r.label()))
                .toList();
    }

    /**
     * 计算比例并保留 4 位小数，避免前端出现长浮点。
     */
    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) return 0;
        return Math.min(1, Math.round(numerator * 10000.0 / denominator) / 10000.0);
    }
}
