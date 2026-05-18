package com.synchat.backend.receipt.dto;

import java.util.UUID;

public record MessageDeliveredPayload(
        UUID conversationId,
        UUID userId,
        Long deliveredSeq
) {
}
