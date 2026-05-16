package com.synchat.backend.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationMetadataResponse(
        UUID conversationId,
        PeerResponse peer,
        Long lastMessageSeq,
        String lastMessageText,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        Integer unreadCount,
        Long peerReadSeq,
        Long peerDeliveredSeq
) {
}
