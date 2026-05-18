package com.synchat.backend.realtime.typing.service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public interface TypingRuntimeStore {

    void startTyping(String socketId, UUID conversationId, UUID userId, Duration ttl);

    boolean stopTyping(String socketId, UUID conversationId, UUID userId);

    Set<ActiveTypingEntry> findActiveTypingEntriesBySocketId(String socketId);

    boolean isTyping(UUID conversationId, UUID userId);

    boolean expireIfInactive(UUID conversationId, UUID userId);
}
