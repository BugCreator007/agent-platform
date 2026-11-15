package com.agentplatform.modules.knowledge.infrastructure.persistence.repository;

import com.agentplatform.modules.knowledge.infrastructure.persistence.mapper.DatasetMapper;

import com.agentplatform.modules.knowledge.infrastructure.persistence.entity.DatasetEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 数据集仓储适配器（基础设施层）。
 *
 * <p>包装 {@link DatasetMapper}，对上层提供实体级别的查询与保存能力；
 * 领域对象与实体之间的转换由 {@code MybatisKnowledgeRepository} 负责。</p>
 */
@Repository
public class DatasetRepository {
    /** MyBatis-Plus 数据集 Mapper。 */
    private final DatasetMapper mapper;

    /**
     * @param mapper 数据集 Mapper
     */
    public DatasetRepository(DatasetMapper mapper) { this.mapper = mapper; }

    /**
     * 查询全部数据集。
     *
     * @return 数据集实体列表
     */
    public List<DatasetEntity> findAll() { return mapper.selectList(null); }
    /**
     * 按主键查询数据集。
     *
     * @param id 数据集 ID
     * @return 数据集实体，不存在时为空
     */
    public Optional<DatasetEntity> findById(String id) { return Optional.ofNullable(mapper.selectById(id)); }
    /**
     * 保存（存在则更新）数据集。
     *
     * @param dataset 数据集实体
     */
    public void save(DatasetEntity dataset) { mapper.insertOrUpdate(dataset); }
    /**
     * 按主键删除数据集。
     *
     * @param id 数据集 ID
     */
    public void deleteById(String id) { mapper.deleteById(id); }
}
