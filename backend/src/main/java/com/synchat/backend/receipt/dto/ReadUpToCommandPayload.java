package com.synchat.backend.receipt.dto;

import java.util.UUID;

public record ReadUpToCommandPayload(
        UUID conversationId,
        Long readSeq
) {
}
