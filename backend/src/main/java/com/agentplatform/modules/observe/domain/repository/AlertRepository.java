package com.agentplatform.modules.observe.domain.repository;

import com.agentplatform.modules.observe.domain.model.Alert;

import java.util.List;
import java.util.Optional;

public interface AlertRepository {
    List<Alert> findAll();
    Optional<Alert> findById(String id);
    void save(Alert alert);
}
