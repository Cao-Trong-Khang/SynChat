package com.synchat.backend.common.security;

import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID sessionId, UUID deviceId) {

    public String name() {
        return userId.toString();
    }
}
