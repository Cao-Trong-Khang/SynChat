package com.synchat.backend.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserEventAppender {

    private final UserEventOffsetRepository userEventOffsetRepository;
    private final UserEventRepository userEventRepository;
    private final ObjectMapper objectMapper;

    public UserEventAppender(
            UserEventOffsetRepository userEventOffsetRepository,
            UserEventRepository userEventRepository,
            ObjectMapper objectMapper
    ) {
        this.userEventOffsetRepository = userEventOffsetRepository;
        this.userEventRepository = userEventRepository;
        this.objectMapper = objectMapper;
    }

    public Long append(UUID userId, String eventType, Object payload, Instant now) {
        UserEventOffsetEntity offset = userEventOffsetRepository.findByUserIdForUpdate(userId)
                .orElseGet(() -> userEventOffsetRepository.save(UserEventOffsetEntity.create(userId, now)));

        Long eventSeq = offset.incrementAndGet(now);
        String jsonPayload = serializePayload(payload);

        userEventRepository.save(UserEventEntity.create(userId, eventSeq, eventType, jsonPayload, now));
        return eventSeq;
    }

    private String serializePayload(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Failed to serialize event payload.");
        }
    }
}
