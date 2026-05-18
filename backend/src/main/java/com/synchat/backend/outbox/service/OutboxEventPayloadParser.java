package com.synchat.backend.outbox.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.outbox.dto.OutboxRealtimeEventPayload;
import com.synchat.backend.outbox.entity.OutboxEventEntity;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventPayloadParser {

    private final ObjectMapper objectMapper;

    public OutboxEventPayloadParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxRealtimeEventPayload parse(OutboxEventEntity event) {
        try {
            OutboxRealtimeEventPayload payload = objectMapper.readValue(
                    event.getPayload(),
                    OutboxRealtimeEventPayload.class
            );
            validate(payload);
            return payload;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidPayload();
        }
    }

    private void validate(OutboxRealtimeEventPayload payload) {
        if (payload.userId() == null
                || payload.eventSeq() == null
                || payload.eventSeq() <= 0
                || payload.eventType() == null
                || payload.eventType().isBlank()
                || payload.payload() == null) {
            throw invalidPayload();
        }
    }

    private AppException invalidPayload() {
        return new AppException(ErrorCode.INTERNAL_ERROR, "Invalid outbox payload.");
    }
}
