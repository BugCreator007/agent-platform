package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.infrastructure.persistence.mapper.EvalRunMapper;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalRunEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评测运行记录表仓储（infrastructure 层）。
 * <p>对 {@link EvalRunMapper} 的薄封装，供多表适配器
 * {@link MybatisEvalRepository} 与种子数据初始化器使用。</p>
 */
@Repository
public class EvalRunRepository {
    private final EvalRunMapper mapper;

    /**
     * @param mapper eval_runs 表 Mapper
     */
    public EvalRunRepository(EvalRunMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部运行记录（顺序由数据库返回顺序决定）。
     *
     * @return 运行记录实体列表
     */
    public List<EvalRunEntity> findAll() { return mapper.selectList(null); }

    /**
     * 新增或更新运行记录。
     *
     * @param run 运行记录实体
     */
    public void save(EvalRunEntity run) { mapper.insertOrUpdate(run); }
}
