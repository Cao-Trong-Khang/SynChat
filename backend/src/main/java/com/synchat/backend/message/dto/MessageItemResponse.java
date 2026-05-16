package com.synchat.backend.message.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageItemResponse(
        Long seq,
        UUID senderId,
        UUID clientMessageId,
        String body,
        Instant createdAtServer
) {
}
