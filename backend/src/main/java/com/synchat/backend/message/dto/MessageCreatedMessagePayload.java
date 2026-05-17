package com.synchat.backend.message.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedMessagePayload(
        Long seq,
        UUID senderId,
        UUID clientMessageId,
        String body,
        Instant createdAtServer
) {
}
