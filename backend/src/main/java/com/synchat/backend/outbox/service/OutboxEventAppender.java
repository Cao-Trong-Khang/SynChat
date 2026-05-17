package com.synchat.backend.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import com.synchat.backend.outbox.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventAppender {

    public static final String STATUS_PENDING = "pending";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventAppender(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    public UUID append(String eventType, Object payload, Instant now) {
        UUID id = UUID.randomUUID();
        String jsonPayload = serializePayload(payload);

        outboxEventRepository.save(OutboxEventEntity.create(
                id,
                eventType,
                jsonPayload,
                STATUS_PENDING,
                now
        ));
        return id;
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to serialize outbox payload.");
        }
    }
}
