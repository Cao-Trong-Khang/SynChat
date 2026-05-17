package com.synchat.backend.message.dto;

import com.synchat.backend.conversation.dto.ConversationMetadataResponse;

public record MessageCreatedPayload(
        ConversationMetadataResponse conversation,
        MessageCreatedMessagePayload message
) {
}
