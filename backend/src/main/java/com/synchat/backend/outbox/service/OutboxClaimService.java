package com.synchat.backend.outbox.service;

import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.model.OutboxEventStatus;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxClaimService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimPending(int batchSize) {
        List<OutboxEventEntity> events = outboxEventRepository.findByStatusForUpdate(
                OutboxEventStatus.PENDING,
                PageRequest.of(0, batchSize)
        );

        for (OutboxEventEntity event : events) {
            event.markProcessing();
        }

        return events.stream()
                .map(event -> new ClaimedOutboxEvent(event.getId(), event.getEventType(), event.getPayload()))
                .toList();
    }
}
