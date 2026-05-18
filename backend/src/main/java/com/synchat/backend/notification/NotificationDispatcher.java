package com.synchat.backend.notification;

import java.util.UUID;

public interface NotificationDispatcher {

    void dispatchForOutboxEvent(UUID userId, String eventType, Object payload);
}
