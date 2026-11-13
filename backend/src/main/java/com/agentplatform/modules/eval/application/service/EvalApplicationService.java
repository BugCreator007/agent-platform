package com.agentplatform.modules.eval.application.service;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class EvalApplicationService {
    private final EvalRepository repository;

    public EvalApplicationService(EvalRepository repository) {
        this.repository = repository;
    }

    public List<EvalSuite> suites() { return repository.suites(); }
    public List<EvalRun> runs() { return repository.runs(); }

    public EvalRun run(String suiteId, String agentName, String version) {
        EvalSuite suite = repository.suites().stream()
                .filter(s -> s.id().equals(suiteId)).findFirst()
                .orElse(repository.suites().get(0));
        double score = Math.round((92 + Math.random() * 5) * 10.0) / 10.0;
        int passed = (int) Math.round(suite.cases() * score / 100);
        EvalRun run = new EvalRun(
                "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6),
                suite.name(), agentName == null ? "售后工单助手" : agentName,
                version == null ? "v25" : version, passed + "/" + suite.cases(),
                score, "5m 20s", "刚刚", "success");
        repository.addRun(run);
        return run;
    }
}
