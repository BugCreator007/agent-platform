package com.agentplatform.modules.release.infrastructure.persistence;

import com.agentplatform.modules.release.domain.model.Release;
import com.agentplatform.modules.release.domain.repository.ReleaseRepository;
import com.agentplatform.modules.release.infrastructure.persistence.entity.ReleaseEntity;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class JpaReleaseRepository implements ReleaseRepository {
    private final ReleaseJpaRepository jpa;
    public JpaReleaseRepository(ReleaseJpaRepository jpa) { this.jpa = jpa; }
    @Override public List<Release> findAll() { return jpa.findAll().stream().map(ReleaseEntity::toDomain).toList(); }
    @Override public Optional<Release> findById(String id) { return jpa.findById(id).map(ReleaseEntity::toDomain); }
    @Override public void save(Release release) { jpa.save(ReleaseEntity.from(release)); }
}
