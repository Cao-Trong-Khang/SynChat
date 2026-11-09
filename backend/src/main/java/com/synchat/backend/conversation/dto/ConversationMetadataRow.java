package com.synchat.backend.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationMetadataRow(
        UUID conversationId,
        UUID peerId,
        String peerEmail,
        String peerDisplayName,
        String peerAvatarUrl,
        Long lastMessageSeq,
        String lastMessageText,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        Integer unreadCount,
        Long peerReadSeq,
        Long peerDeliveredSeq
) {

    public ConversationMetadataResponse toResponse() {
        return new ConversationMetadataResponse(
                conversationId,
                new PeerResponse(peerId, peerEmail, peerDisplayName, peerAvatarUrl),
                lastMessageSeq,
                lastMessageText,
                lastMessageSenderId,
                lastMessageAt,
                unreadCount,
                peerReadSeq,
                peerDeliveredSeq
        );
    }
}
