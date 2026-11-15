package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.domain.model.EvalRun;
import com.agentplatform.modules.eval.domain.model.EvalSuite;
import com.agentplatform.modules.eval.domain.repository.EvalRepository;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评测仓储的多表适配器（infrastructure 层）。
 * <p>实现 domain 层 {@link EvalRepository} 端口：聚合评测集与评测运行两个表级
 * Repository，完成实体到领域对象的转换。标注 {@link Primary} 明确主实现，
 * 避免将来出现多个适配器时注入歧义。</p>
 */
@Primary
@Repository
public class MybatisEvalRepository implements EvalRepository {
    private final EvalSuiteRepository suites;
    private final EvalRunRepository runs;

    /**
     * @param suites 评测集表仓储
     * @param runs   评测运行表仓储
     */
    public MybatisEvalRepository(EvalSuiteRepository suites, EvalRunRepository runs) { this.suites = suites; this.runs = runs; }

    /** {@inheritDoc} */
    @Override public List<EvalSuite> suites() { return suites.findAll().stream().map(EvalSuiteEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public List<EvalRun> runs() { return runs.findAll().stream().map(EvalRunEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public void addRun(EvalRun run) { runs.save(EvalRunEntity.from(run)); }
}
