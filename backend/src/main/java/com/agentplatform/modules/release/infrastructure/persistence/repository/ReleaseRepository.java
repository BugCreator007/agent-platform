package com.agentplatform.modules.release.infrastructure.persistence.repository;

import com.agentplatform.modules.release.infrastructure.persistence.mapper.ReleaseMapper;

import com.agentplatform.modules.release.domain.model.Release;
import com.agentplatform.modules.release.infrastructure.persistence.entity.ReleaseEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ReleaseRepository implements com.agentplatform.modules.release.domain.repository.ReleaseRepository {
    private final ReleaseMapper mapper;
    public ReleaseRepository(ReleaseMapper mapper) { this.mapper = mapper; }
    @Override public List<Release> findAll() { return mapper.selectList(null).stream().map(ReleaseEntity::toDomain).toList(); }
    @Override public Optional<Release> findById(String id) { return Optional.ofNullable(mapper.selectById(id)).map(ReleaseEntity::toDomain); }
    @Override public void save(Release release) { mapper.insertOrUpdate(ReleaseEntity.from(release)); }
}
