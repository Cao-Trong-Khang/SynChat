package com.synchat.backend.receipt.dto;

import java.util.UUID;

public record DeliveredUpToCommandPayload(
        UUID conversationId,
        Long deliveredSeq
) {
}
