package com.agentplatform.modules.eval.infrastructure.persistence;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.demo.seed", matchIfMissing = true)
public class EvalDataSeeder implements ApplicationRunner {
    private final EvalSuiteJpaRepository suites;
    private final EvalRunJpaRepository runs;
    public EvalDataSeeder(EvalSuiteJpaRepository suites, EvalRunJpaRepository runs) { this.suites = suites; this.runs = runs; }
    @Override public void run(ApplicationArguments args) {
        if (suites.count() > 0) return;
        EvalSeedData seed = new EvalSeedData();
        seed.suites().forEach(s -> suites.save(EvalSuiteEntity.from(s)));
        seed.runs().forEach(r -> runs.save(EvalRunEntity.from(r)));
    }
}
