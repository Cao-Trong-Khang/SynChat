package com.synchat.backend.receipt.dto;

import java.util.UUID;

public record MessageReadPayload(
        UUID conversationId,
        UUID userId,
        Long readSeq
) {
}
