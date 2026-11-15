package com.agentplatform.modules.eval.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;

/**
 * 发布评测门禁服务（application 层，跨模块 API）。
 * <p>发布模块在审批发布单前调用 {@link #check}：要求发布单得分（如有）以及
 * 该智能体对应版本的最近一次评测都达到最低分且状态为成功，否则拒绝审批。
 * 最低分通过配置 {@code app.release.min-eval-score} 调整，默认 90。</p>
 */
@Service
public class EvalGateService {
    private final EvalRepository repository;
    private final double minScore;

    /**
     * @param repository 评测仓储端口，用于查询最近评测记录
     * @param minScore   发布门禁最低分，来自 {@code app.release.min-eval-score}，默认 90
     */
    public EvalGateService(EvalRepository repository,
                           @Value("${app.release.min-eval-score:90}") double minScore) {
        this.repository = repository;
        this.minScore = minScore;
    }

    /**
     * 校验某智能体版本是否满足发布门禁。
     * <p>校验顺序：先看发布单自带得分（非 null 时必须达标），再按智能体名称与版本
     * 查找最近一次评测记录；找不到记录、状态非 success 或得分低于门禁均拒绝。</p>
     *
     * @param agentName    智能体名称（与评测记录中的名称匹配）
     * @param version      版本号；为 null/空白时匹配该智能体的任意版本
     * @param releaseScore 发布单上的评测得分，可为 null（表示以最近评测记录为准）
     * @throws BusinessException 得分不足、无评测记录或最近评测未通过时抛出（STATE_CONFLICT）
     */
    public void check(String agentName, String version, Double releaseScore) {
        if (releaseScore != null && releaseScore < minScore) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "评测得分低于发布门禁 " + minScore + "，不能审批");
        }
        // 运行时间字段是展示文本，按字符串最大值近似“最近一次”；接入真实时间戳后应替换
        EvalRun latest = repository.runs().stream()
                .filter(r -> r.agent().equals(agentName))
                .filter(r -> version == null || version.isBlank() || r.version().equals(version))
                .max(Comparator.comparing(EvalRun::time))
                .orElse(null);
        if (latest == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "没有找到该版本的评测记录，不能审批");
        }
        if (!"success".equalsIgnoreCase(latest.status()) || latest.score() < minScore) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "最近评测未通过门禁（" + latest.score() + " 分），不能审批");
        }
    }
}
