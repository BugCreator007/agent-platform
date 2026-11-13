package com.agentplatform.modules.system.interfaces.dto;

import java.util.List;

public record AuthUser(String id, String username, String displayName, List<String> roles) {
}
