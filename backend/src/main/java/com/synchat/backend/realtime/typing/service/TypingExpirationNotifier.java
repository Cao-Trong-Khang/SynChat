package com.synchat.backend.realtime.typing.service;

import java.util.UUID;

public interface TypingExpirationNotifier {

    void onTypingExpired(UUID conversationId, UUID userId);
}
