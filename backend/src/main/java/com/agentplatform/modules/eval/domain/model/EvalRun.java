package com.agentplatform.modules.eval.domain.model;

public record EvalRun(
        String id, String suite, String agent, String version, String passed,
        double score, String duration, String time, String status
) {
}
