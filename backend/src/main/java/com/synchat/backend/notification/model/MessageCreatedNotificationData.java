package com.synchat.backend.notification.model;

import java.util.UUID;

public record MessageCreatedNotificationData(
        UUID conversationId,
        UUID senderId,
        UUID recipientUserId,
        String senderDisplayName,
        String messagePreview,
        Long messageSeq,
        UUID clientMessageId
) {
}
