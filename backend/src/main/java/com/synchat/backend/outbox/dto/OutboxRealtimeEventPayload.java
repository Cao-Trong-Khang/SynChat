package com.synchat.backend.outbox.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record OutboxRealtimeEventPayload(
        UUID userId,
        Long eventSeq,
        String eventType,
        JsonNode payload
) {
}
