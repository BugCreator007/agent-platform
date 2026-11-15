package com.agentplatform.modules.release.infrastructure.persistence.repository;

import com.agentplatform.modules.release.infrastructure.persistence.mapper.ReleaseMapper;

import com.agentplatform.modules.release.domain.model.Release;
import com.agentplatform.modules.release.infrastructure.persistence.entity.ReleaseEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 发布记录仓储适配器（infrastructure 层）。
 * <p>实现 domain 层 {@link com.agentplatform.modules.release.domain.repository.ReleaseRepository}
 * 端口，委托 MyBatis-Plus Mapper 并在实体与领域聚合间转换；
 * {@code save} 通过 {@code insertOrUpdate} 实现 upsert。</p>
 */
@Repository
public class ReleaseRepository implements com.agentplatform.modules.release.domain.repository.ReleaseRepository {
    private final ReleaseMapper mapper;

    /**
     * @param mapper releases 表 Mapper
     */
    public ReleaseRepository(ReleaseMapper mapper) { this.mapper = mapper; }

    /** {@inheritDoc} */
    @Override public List<Release> findAll() { return mapper.selectList(null).stream().map(ReleaseEntity::toDomain).toList(); }
    /** {@inheritDoc} */
    @Override public Optional<Release> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(ReleaseEntity::toDomain); }
    /** {@inheritDoc} */
    @Override public void save(Release release) { mapper.insertOrUpdate(ReleaseEntity.from(release)); }
}
