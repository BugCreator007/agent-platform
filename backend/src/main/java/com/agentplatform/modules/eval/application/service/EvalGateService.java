package com.agentplatform.modules.eval.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class EvalGateService {
    private final EvalRepository repository;
    private final double minScore;

    public EvalGateService(EvalRepository repository,
                           @Value("${app.release.min-eval-score:90}") double minScore) {
        this.repository = repository;
        this.minScore = minScore;
    }

    public void check(String agentName, String version, Double releaseScore) {
        if (releaseScore != null && releaseScore < minScore) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT,
                    "评测得分低于发布门禁 " + minScore + "，不能审批");
        }
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
