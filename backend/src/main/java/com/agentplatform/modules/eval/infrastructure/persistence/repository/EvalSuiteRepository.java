package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.infrastructure.persistence.mapper.EvalSuiteMapper;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EvalSuiteRepository {
    private final EvalSuiteMapper mapper;

    public EvalSuiteRepository(EvalSuiteMapper mapper) { this.mapper = mapper; }

    public List<EvalSuiteEntity> findAll() { return mapper.selectList(null); }
    public long count() { return mapper.selectCount(null); }
    public void save(EvalSuiteEntity suite) { mapper.insertOrUpdate(suite); }
}
