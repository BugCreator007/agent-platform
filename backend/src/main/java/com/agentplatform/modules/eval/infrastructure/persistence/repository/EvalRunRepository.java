package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.infrastructure.persistence.mapper.EvalRunMapper;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class EvalRunRepository {
    private final EvalRunMapper mapper;

    public EvalRunRepository(EvalRunMapper mapper) { this.mapper = mapper; }

    public List<EvalRunEntity> findAll() { return mapper.selectList(null); }
    public void save(EvalRunEntity run) { mapper.insertOrUpdate(run); }
}
