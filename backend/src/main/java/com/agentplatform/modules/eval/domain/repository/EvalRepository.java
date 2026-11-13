package com.agentplatform.modules.eval.domain.repository;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;

import java.util.List;

public interface EvalRepository {
    List<EvalSuite> suites();
    List<EvalRun> runs();
    void addRun(EvalRun run);
}
