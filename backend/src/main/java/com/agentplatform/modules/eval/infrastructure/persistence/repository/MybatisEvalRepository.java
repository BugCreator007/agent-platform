package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

@Primary
@Repository
public class MybatisEvalRepository implements EvalRepository {
    private final EvalSuiteRepository suites;
    private final EvalRunRepository runs;
    public MybatisEvalRepository(EvalSuiteRepository suites, EvalRunRepository runs) { this.suites = suites; this.runs = runs; }
    @Override public List<EvalSuite> suites() { return suites.findAll().stream().map(EvalSuiteEntity::toDomain).toList(); }
    @Override public List<EvalRun> runs() { return runs.findAll().stream().map(EvalRunEntity::toDomain).toList(); }
    @Override public void addRun(EvalRun run) { runs.save(EvalRunEntity.from(run)); }
}
