package com.agentplatform.modules.workflow.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowNode(String key, String type, String title, String meta, Map<String, Object> config) {
    public WorkflowNode {
        config = config == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(config));
    }

    public WorkflowNode(String key, String type, String title, String meta) {
        this(key, type, title, meta, Map.of());
    }
}
