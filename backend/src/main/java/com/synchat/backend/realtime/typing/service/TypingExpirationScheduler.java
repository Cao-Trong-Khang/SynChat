package com.synchat.backend.realtime.typing.service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TypingExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(TypingExpirationScheduler.class);

    private final TypingRuntimeStore typingRuntimeStore;
    private final TypingExpirationNotifier typingExpirationNotifier;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public TypingExpirationScheduler(
            TypingRuntimeStore typingRuntimeStore,
            TypingExpirationNotifier typingExpirationNotifier
    ) {
        this.typingRuntimeStore = typingRuntimeStore;
        this.typingExpirationNotifier = typingExpirationNotifier;
    }

    public void scheduleExpirationCheck(UUID conversationId, UUID userId, Duration ttl) {
        executorService.schedule(
                () -> checkExpiration(conversationId, userId),
                ttl.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    private void checkExpiration(UUID conversationId, UUID userId) {
        try {
            if (typingRuntimeStore.expireIfInactive(conversationId, userId)) {
                typingExpirationNotifier.onTypingExpired(conversationId, userId);
            }
        } catch (Exception exception) {
            log.warn("Failed to check typing expiration.", exception);
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
