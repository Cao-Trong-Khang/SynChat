package com.synchat.backend.common.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, UUID sessionId, UUID deviceId) {
}
