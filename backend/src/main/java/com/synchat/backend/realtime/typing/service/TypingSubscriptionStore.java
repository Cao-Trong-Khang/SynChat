package com.synchat.backend.realtime.typing.service;

import java.util.Set;
import java.util.UUID;

public interface TypingSubscriptionStore {

    void subscribe(String socketId, UUID conversationId);

    void unsubscribe(String socketId, UUID conversationId);

    void unsubscribeAll(String socketId);

    Set<String> findSubscriberSocketIds(UUID conversationId);
}
