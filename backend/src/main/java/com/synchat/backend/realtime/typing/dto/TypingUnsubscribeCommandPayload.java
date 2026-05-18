package com.synchat.backend.realtime.typing.dto;

import java.util.UUID;

public record TypingUnsubscribeCommandPayload(UUID conversationId) {
}
