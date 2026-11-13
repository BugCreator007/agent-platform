package com.agentplatform.modules.system.domain.model;

import java.util.List;

public record UserAccount(String id, String username, String passwordHash, String displayName,
                          List<String> roles, boolean enabled) {
    public boolean hasRole(String role) { return roles.contains(role); }
}
