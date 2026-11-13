package com.agentplatform.modules.tool.domain.repository;

import com.agentplatform.modules.tool.domain.model.Tool;

import java.util.List;
import java.util.Optional;

public interface ToolRepository {
    List<Tool> findAll();
    Optional<Tool> findByName(String name);
    void save(Tool tool);
}
