package com.agentplatform.modules.release.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.release.domain.model.Release;
import com.agentplatform.modules.release.domain.repository.ReleaseRepository;
import com.agentplatform.modules.eval.application.service.EvalGateService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReleaseApplicationService {
    private final ReleaseRepository repository;
    private final EvalGateService evalGate;

    public ReleaseApplicationService(ReleaseRepository repository, EvalGateService evalGate) {
        this.repository = repository;
        this.evalGate = evalGate;
    }

    public List<Release> list() { return repository.findAll(); }

    public Release approve(String id) {
        Release release = require(id);
        evalGate.check(release.agentName(), release.version(), release.evalScore());
        try { release.approve(); } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, e.getMessage());
        }
        repository.save(release);
        return release;
    }

    public Release promote(String id) {
        Release release = require(id);
        try { release.promote(); } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, e.getMessage());
        }
        repository.save(release);
        return release;
    }

    public Release updateTraffic(String id, int traffic) {
        Release release = require(id);
        release.updateTraffic(traffic);
        repository.save(release);
        return release;
    }

    public Release rollback(String id) {
        Release release = require(id);
        release.rollback();
        repository.save(release);
        return release;
    }

    private Release require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "发布记录不存在: " + id));
    }
}
