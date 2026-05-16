package com.synchat.backend.realtime.websocket.session;

import java.time.Instant;
import java.util.UUID;

public record UserSessionBinding(
        String socketId,
        UUID userId,
        UUID sessionId,
        UUID deviceId,
        Instant connectedAt
) {
}
