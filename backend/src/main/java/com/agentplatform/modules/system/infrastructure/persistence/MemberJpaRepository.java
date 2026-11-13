package com.agentplatform.modules.system.infrastructure.persistence;

import com.agentplatform.modules.system.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberJpaRepository extends JpaRepository<MemberEntity, String> {
}
