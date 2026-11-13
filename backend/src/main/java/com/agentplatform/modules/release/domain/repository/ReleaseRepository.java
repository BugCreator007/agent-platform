package com.agentplatform.modules.release.domain.repository;

import com.agentplatform.modules.release.domain.model.Release;

import java.util.List;
import java.util.Optional;

public interface ReleaseRepository {
    List<Release> findAll();
    Optional<Release> findById(String id);
    void save(Release release);
}
