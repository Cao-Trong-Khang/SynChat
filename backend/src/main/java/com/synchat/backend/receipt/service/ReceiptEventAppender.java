package com.synchat.backend.receipt.service;

import com.synchat.backend.outbox.service.OutboxEventAppender;
import com.synchat.backend.receipt.dto.MessageDeliveredPayload;
import com.synchat.backend.receipt.dto.MessageReadPayload;
import com.synchat.backend.receipt.model.ReceiptEventTypes;
import com.synchat.backend.sync.service.UserEventAppender;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ReceiptEventAppender {

    private final UserEventAppender userEventAppender;
    private final OutboxEventAppender outboxEventAppender;

    public ReceiptEventAppender(UserEventAppender userEventAppender, OutboxEventAppender outboxEventAppender) {
        this.userEventAppender = userEventAppender;
        this.outboxEventAppender = outboxEventAppender;
    }

    public void appendDeliveredEvent(UUID targetUserId, MessageDeliveredPayload payload, Instant now) {
        append(targetUserId, ReceiptEventTypes.MESSAGE_DELIVERED, payload, now);
    }

    public void appendReadEvent(UUID targetUserId, MessageReadPayload payload, Instant now) {
        append(targetUserId, ReceiptEventTypes.MESSAGE_READ, payload, now);
    }

    private void append(UUID targetUserId, String eventType, Object payload, Instant now) {
        Long eventSeq = userEventAppender.append(targetUserId, eventType, payload, now);
        outboxEventAppender.append(eventType, new OutboxRealtimePayload(targetUserId, eventSeq, eventType, payload), now);
    }

    private record OutboxRealtimePayload(UUID userId, Long eventSeq, String eventType, Object payload) {
    }
}
