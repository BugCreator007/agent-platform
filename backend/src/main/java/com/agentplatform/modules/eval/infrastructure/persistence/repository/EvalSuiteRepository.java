package com.agentplatform.modules.eval.infrastructure.persistence.repository;

import com.agentplatform.modules.eval.infrastructure.persistence.mapper.EvalSuiteMapper;

import com.agentplatform.modules.eval.infrastructure.persistence.entity.EvalSuiteEntity;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评测集表仓储（infrastructure 层）。
 * <p>对 {@link EvalSuiteMapper} 的薄封装，供多表适配器
 * {@link MybatisEvalRepository} 与种子数据初始化器使用。</p>
 */
@Repository
public class EvalSuiteRepository {
    private final EvalSuiteMapper mapper;

    /**
     * @param mapper eval_suites 表 Mapper
     */
    public EvalSuiteRepository(EvalSuiteMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部评测集。
     *
     * @return 评测集实体列表
     */
    public List<EvalSuiteEntity> findAll() { return mapper.selectList(null); }

    /**
     * 统计评测集数量，种子数据初始化用它判断是否已初始化。
     *
     * @return 评测集总数
     */
    public long count() { return mapper.selectCount(null); }

    /**
     * 新增或更新评测集。
     *
     * @param suite 评测集实体
     */
    public void save(EvalSuiteEntity suite) { mapper.insertOrUpdate(suite); }
}
