package com.synchat.backend.realtime.presence.service;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface PresenceSubscriptionStore {

    void subscribe(String socketId, Collection<UUID> targetUserIds);

    void unsubscribe(String socketId, Collection<UUID> targetUserIds);

    void unsubscribeAll(String socketId);

    Set<String> findWatcherSocketIds(UUID targetUserId);
}
