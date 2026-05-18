package com.synchat.backend.realtime.presence.dto;

import java.time.Instant;
import java.util.UUID;

public record PresenceChangedPayload(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {
}
