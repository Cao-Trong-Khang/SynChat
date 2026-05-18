package com.synchat.backend.outbox.service;

import com.synchat.backend.notification.NotificationDispatcher;
import com.synchat.backend.outbox.dto.OutboxRealtimeEventPayload;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import com.synchat.backend.realtime.websocket.RealtimeEventPublisher;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OutboxPublishService {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventPayloadParser payloadParser;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final NotificationDispatcher notificationDispatcher;
    private final OutboxStatusService outboxStatusService;

    public OutboxPublishService(
            OutboxEventRepository outboxEventRepository,
            OutboxEventPayloadParser payloadParser,
            RealtimeEventPublisher realtimeEventPublisher,
            NotificationDispatcher notificationDispatcher,
            OutboxStatusService outboxStatusService
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.payloadParser = payloadParser;
        this.realtimeEventPublisher = realtimeEventPublisher;
        this.notificationDispatcher = notificationDispatcher;
        this.outboxStatusService = outboxStatusService;
    }

    public void publish(ClaimedOutboxEvent claimed) {
        if (claimed == null) {
            log.warn("Failed to publish outbox event because claimed event is null.");
            return;
        }

        try {
            Optional<OutboxEventEntity> event = outboxEventRepository.findById(claimed.id());
            if (event.isEmpty()) {
                return;
            }

            OutboxRealtimeEventPayload payload = payloadParser.parse(event.get());
            realtimeEventPublisher.publishToUser(
                    payload.userId(),
                    payload.eventSeq(),
                    payload.eventType(),
                    payload.payload()
            );
            notificationDispatcher.dispatchForOutboxEvent(
                    payload.userId(),
                    payload.eventType(),
                    payload.payload()
            );
            outboxStatusService.markPublished(claimed.id());
        } catch (Exception exception) {
            log.warn("Failed to publish outbox event id={}.", claimed.id(), exception);
            markFailed(claimed);
        }
    }

    private void markFailed(ClaimedOutboxEvent claimed) {
        try {
            outboxStatusService.markFailed(claimed.id());
        } catch (Exception exception) {
            log.warn("Failed to mark outbox event id={} as failed.", claimed.id(), exception);
        }
    }
}
