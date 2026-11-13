package com.agentplatform.modules.workflow.application.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persisted execution state. It is intentionally small: completed/skipped nodes,
 * node outputs, selected condition edges and the compensation stack.
 */
public class WorkflowRunState {
    public Set<String> completed = new LinkedHashSet<>();
    public Set<String> skipped = new LinkedHashSet<>();
    public Map<String, String> outputs = new LinkedHashMap<>();
    public Set<String> inactiveEdges = new LinkedHashSet<>();
    public List<CompensationRecord> compensations = new ArrayList<>();
    public String waitingNode;
    public String subflowInstanceId;

    public static class CompensationRecord {
        public String nodeKey;
        public String tool;
        public String inputJson;

        public CompensationRecord() {
        }

        public CompensationRecord(String nodeKey, String tool, String inputJson) {
            this.nodeKey = nodeKey;
            this.tool = tool;
            this.inputJson = inputJson;
        }
    }
}
