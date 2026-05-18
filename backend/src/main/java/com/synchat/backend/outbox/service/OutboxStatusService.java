package com.synchat.backend.outbox.service;

import com.synchat.backend.outbox.repository.OutboxEventRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxStatusService {

    private final OutboxEventRepository outboxEventRepository;

    public OutboxStatusService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public void markPublished(UUID id) {
        outboxEventRepository.findById(id)
                .ifPresent(event -> event.markPublished());
    }

    @Transactional
    public void markFailed(UUID id) {
        outboxEventRepository.findById(id)
                .ifPresent(event -> event.markFailed());
    }
}
