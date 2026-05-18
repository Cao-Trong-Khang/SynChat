package com.synchat.backend.notification;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(NotificationDispatcher.class)
public class NoopNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NoopNotificationDispatcher.class);

    @Override
    public void dispatchForOutboxEvent(UUID userId, String eventType, Object payload) {
        log.debug("Notification dispatch skipped for eventType={} userId={}.", eventType, userId);
    }
}
