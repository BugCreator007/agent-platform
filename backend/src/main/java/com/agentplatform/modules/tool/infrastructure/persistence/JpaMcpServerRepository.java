package com.agentplatform.modules.tool.infrastructure.persistence;

import com.agentplatform.framework.security.SecretCipher;
import com.agentplatform.modules.tool.domain.model.McpServer;
import com.agentplatform.modules.tool.domain.repository.McpServerRepository;
import com.agentplatform.modules.tool.infrastructure.persistence.entity.McpServerEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMcpServerRepository implements McpServerRepository {
    private final McpServerJpaRepository jpa;
    private final SecretCipher cipher;
    public JpaMcpServerRepository(McpServerJpaRepository jpa, SecretCipher cipher) { this.jpa=jpa; this.cipher=cipher; }
    @Override public List<McpServer> findAll() { return jpa.findAll().stream().map(e -> e.toDomain(cipher)).toList(); }
    @Override public Optional<McpServer> findById(String id) { return jpa.findById(id).map(e -> e.toDomain(cipher)); }
    @Override public void save(McpServer server) { jpa.save(McpServerEntity.from(server, cipher)); }
    @Override public void deleteById(String id) { jpa.deleteById(id); }
}
