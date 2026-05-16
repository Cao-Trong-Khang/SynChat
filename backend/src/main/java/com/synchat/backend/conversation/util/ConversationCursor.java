package com.synchat.backend.conversation.util;

import java.time.Instant;
import java.util.UUID;

public record ConversationCursor(Instant lastMessageAt, UUID conversationId) {
}
