package com.synchat.backend.message.dto;

import java.util.UUID;

public record SendMessageCommandPayload(
        UUID conversationId,
        UUID recipientId,
        UUID clientMessageId,
        String body
) {
}
