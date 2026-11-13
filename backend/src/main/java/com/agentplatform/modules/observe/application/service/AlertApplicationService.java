package com.agentplatform.modules.observe.application.service;

import com.agentplatform.common.error.BusinessException;
import com.agentplatform.common.error.ErrorCode;
import com.agentplatform.modules.observe.domain.model.Alert;
import com.agentplatform.modules.observe.domain.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertApplicationService {
    private final AlertRepository repository;

    public AlertApplicationService(AlertRepository repository) {
        this.repository = repository;
    }

    public List<Alert> list(String status) {
        return repository.findAll().stream()
                .filter(a -> status == null || status.isBlank() || a.status().equals(status)).toList();
    }

    public Alert ack(String id) {
        Alert alert = require(id);
        alert.ack();
        repository.save(alert);
        return alert;
    }

    public Alert resolve(String id) {
        Alert alert = require(id);
        alert.resolve();
        repository.save(alert);
        return alert;
    }

    private Alert require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "告警不存在: " + id));
    }
}
