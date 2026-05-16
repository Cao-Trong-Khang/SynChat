package com.synchat.backend.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synchat.backend.common.exception.AppException;
import com.synchat.backend.common.exception.ErrorCode;
import com.synchat.backend.common.security.AuthenticatedUser;
import com.synchat.backend.common.security.CurrentUserProvider;
import com.synchat.backend.sync.dto.SyncEventItemResponse;
import com.synchat.backend.sync.dto.SyncEventsResponse;
import com.synchat.backend.sync.entity.UserEventEntity;
import com.synchat.backend.sync.entity.UserEventOffsetEntity;
import com.synchat.backend.sync.repository.UserEventOffsetRepository;
import com.synchat.backend.sync.repository.UserEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncService {

    private final CurrentUserProvider currentUserProvider;
    private final UserEventRepository userEventRepository;
    private final UserEventOffsetRepository userEventOffsetRepository;
    private final ObjectMapper objectMapper;

    public SyncService(
            CurrentUserProvider currentUserProvider,
            UserEventRepository userEventRepository,
            UserEventOffsetRepository userEventOffsetRepository,
            ObjectMapper objectMapper
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userEventRepository = userEventRepository;
        this.userEventOffsetRepository = userEventOffsetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public SyncEventsResponse syncEvents(Long afterSeq, Integer limit) {
        if (afterSeq == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "afterSeq is required.");
        }
        if (afterSeq < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid afterSeq.");
        }

        int pageSize = validateLimit(limit);
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        UUID currentUserId = currentUser.userId();
        Long lastEventSeq = userEventOffsetRepository.findById(currentUserId)
                .map(UserEventOffsetEntity::getLastEventSeq)
                .orElse(0L);

        if (afterSeq > lastEventSeq) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "afterSeq is greater than server event sequence."
            );
        }

        validateCursorNotExpired(currentUserId, afterSeq, lastEventSeq);

        List<UserEventEntity> fetched = userEventRepository.findByUserIdAndEventSeqGreaterThanOrderByEventSeqAsc(
                currentUserId,
                afterSeq,
                PageRequest.of(0, pageSize + 1)
        );

        boolean hasMore = fetched.size() > pageSize;
        List<UserEventEntity> returnedEvents = hasMore ? fetched.subList(0, pageSize) : fetched;
        Long nextAfterSeq = returnedEvents.isEmpty()
                ? afterSeq
                : returnedEvents.get(returnedEvents.size() - 1).getEventSeq();

        List<SyncEventItemResponse> events = returnedEvents.stream()
                .map(this::toResponse)
                .toList();

        return new SyncEventsResponse(events, hasMore, nextAfterSeq);
    }

    private int validateLimit(Integer limit) {
        int value = limit == null ? 500 : limit;
        if (value < 1 || value > 1000) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Invalid limit.");
        }
        return value;
    }

    private void validateCursorNotExpired(UUID currentUserId, Long afterSeq, Long lastEventSeq) {
        if (lastEventSeq <= 0) {
            return;
        }

        Optional<Long> minEventSeq = userEventRepository.findMinEventSeqByUserId(currentUserId);
        if (minEventSeq.isPresent() && afterSeq < minEventSeq.get() - 1) {
            throw cursorExpired();
        }

        if (minEventSeq.isEmpty() && afterSeq < lastEventSeq) {
            throw cursorExpired();
        }
    }

    private SyncEventItemResponse toResponse(UserEventEntity event) {
        return new SyncEventItemResponse(
                event.getEventSeq(),
                event.getEventType(),
                readPayload(event.getPayload())
        );
    }

    private Object readPayload(String payload) {
        try {
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception exception) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Invalid event payload.");
        }
    }

    private AppException cursorExpired() {
        return new AppException(ErrorCode.EVENT_CURSOR_EXPIRED, "Event cursor is too old.");
    }
}
