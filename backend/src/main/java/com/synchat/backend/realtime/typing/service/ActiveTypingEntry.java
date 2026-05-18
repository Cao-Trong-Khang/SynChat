package com.synchat.backend.realtime.typing.service;

import java.util.UUID;

public record ActiveTypingEntry(UUID conversationId, UUID userId) {
}
