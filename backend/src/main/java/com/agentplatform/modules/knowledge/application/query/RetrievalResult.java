package com.agentplatform.modules.knowledge.application.query;

import java.util.List;

public record RetrievalResult(
        String query, long ms, String mode, int topK, double threshold, List<RetrievalHit> hits
) {
}
